package dev.ajay.bank.payment;
import dev.ajay.bank.common.*;
import java.util.*;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.*;
import io.github.resilience4j.circuitbreaker.*;
import io.micrometer.core.instrument.MeterRegistry;
@Component @ConditionalOnProperty(name="bank.workers-enabled",havingValue="true",matchIfMissing=true)
public class PaymentWorker {
 private final JdbcTemplate db;private final TransactionTemplate tx;private final RestClient client;private final Outbox outbox;private final MeterRegistry meters;
 private final String ledgerUrl,fraudUrl,accountUrl;
 private final CircuitBreaker ledgerBreaker=breaker("ledger"),fraudBreaker=breaker("fraud"),accountBreaker=breaker("account");
 private static CircuitBreaker breaker(String name){return CircuitBreaker.of(name,CircuitBreakerConfig.custom().slidingWindowSize(10).minimumNumberOfCalls(5).failureRateThreshold(50).waitDurationInOpenState(Duration.ofSeconds(10)).ignoreExceptions(HttpClientErrorException.class).build());}
 public PaymentWorker(JdbcTemplate db,TransactionTemplate tx,RestClient client,Outbox outbox,MeterRegistry meters,@Value("${bank.ledger-url}")String ledgerUrl,@Value("${bank.fraud-url}")String fraudUrl,@Value("${bank.account-url}")String accountUrl){this.db=db;this.tx=tx;this.client=client;this.outbox=outbox;this.meters=meters;this.ledgerUrl=ledgerUrl;this.fraudUrl=fraudUrl;this.accountUrl=accountUrl;}
 /** One durable step per transaction. The row lock prevents two workers advancing the same saga. */
 @Scheduled(fixedDelayString="${bank.worker-delay:250}") public void tick(){
  tx.executeWithoutResult(status->{
   var rows=db.queryForList("SELECT * FROM payment WHERE state IN ('NEW','RESERVED','APPROVED','RELEASING') AND next_attempt_at<=now() ORDER BY created_at LIMIT 1 FOR UPDATE SKIP LOCKED");
   if(rows.isEmpty())return;var p=rows.get(0);UUID id=(UUID)p.get("id");String state=(String)p.get("state");
   try {
    switch(state){
     case "NEW" -> {
      accountBreaker.executeSupplier(()->client.get().uri(accountUrl+"/internal/accounts/"+p.get("source")+"/owner/"+p.get("owner")).retrieve().toBodilessEntity());
      var command=new LedgerCommand(id,(String)p.get("owner"),new TransferCommand((UUID)p.get("source"),(UUID)p.get("destination"),((Number)p.get("amount_minor")).longValue(),(String)p.get("currency")));
      Map<?,?> result=ledgerBreaker.executeSupplier(()->client.post().uri(ledgerUrl+"/internal/ledger/reserve").body(command).retrieve().body(Map.class));
      switch(String.valueOf(result.get("state"))){case "RESERVED" -> advance(id,"RESERVED",null);case "REJECTED" -> advance(id,"REJECTED","Insufficient available funds");default -> throw new IllegalStateException("Unexpected reservation state");}
     }
     case "RESERVED" -> {
      Map<?,?> result=fraudBreaker.executeSupplier(()->client.post().uri(fraudUrl+"/internal/fraud/check").body(Map.of("paymentId",id,"owner",p.get("owner"),"amountMinor",p.get("amount_minor"))).retrieve().body(Map.class));
      Object approved=result.get("approved");if(!(approved instanceof Boolean))throw new IllegalStateException("Malformed fraud response");
      advance(id,Boolean.TRUE.equals(approved)?"APPROVED":"RELEASING",String.valueOf(result.get("reason")));
     }
     case "APPROVED" -> {
      Map<?,?> result=ledgerBreaker.executeSupplier(()->client.post().uri(ledgerUrl+"/internal/ledger/"+id+"/settle").retrieve().body(Map.class));
      if(!"POSTED".equals(result.get("state")))throw new IllegalStateException("Unexpected settlement state");advance(id,"COMPLETED",null);
     }
     case "RELEASING" -> {
      Map<?,?> result=ledgerBreaker.executeSupplier(()->client.post().uri(ledgerUrl+"/internal/ledger/"+id+"/release").retrieve().body(Map.class));
      if(!"RELEASED".equals(result.get("state")))throw new IllegalStateException("Unexpected release state");advance(id,"REJECTED","Fraud policy declined; reservation released");
     }
     default -> throw new IllegalStateException("Unknown state");
    }
   } catch(HttpClientErrorException e){
    // These pre-reservation errors are definitive; a timeout/5xx is NEVER a business rejection.
    int code=e.getStatusCode().value();
    if(state.equals("NEW")&&(code==403||code==404||code==422))advance(id,"REJECTED","Source ownership, account or currency validation failed");else retry(p,e.getClass().getSimpleName());
   } catch(RestClientException|CallNotPermittedException|IllegalStateException e){retry(p,e.getClass().getSimpleName());}
  });
 }
 private void advance(UUID id,String next,String reason){
  db.update("UPDATE payment SET state=?,reason=?,attempts=0,next_attempt_at=now(),updated_at=now() WHERE id=?",next,reason,id);
  db.update("INSERT INTO payment_audit(payment_id,actor,action) VALUES (?,'worker',?)",id,next);
  outbox.append(id,"Payment"+next,Map.of("state",next));meters.counter("bank.payment.transitions","state",next).increment();
 }
 private void retry(Map<String,Object> p,String error){
  int n=((Number)p.get("attempts")).intValue()+1;boolean manual=n>=8;
  double delay=MoneyRules.retrySeconds(n)+ThreadLocalRandom.current().nextDouble();
  db.update("UPDATE payment SET state=?,resume_state=?,attempts=?,reason=?,next_attempt_at=now()+(? * interval '1 second'),updated_at=now() WHERE id=?",manual?"MANUAL_REVIEW":p.get("state"),manual?p.get("state"):null,n,error,delay,p.get("id"));
  meters.counter("bank.payment.retries").increment();
  if(manual){db.update("INSERT INTO payment_audit(payment_id,actor,action) VALUES (?,'worker','MANUAL_REVIEW')",p.get("id"));outbox.append((UUID)p.get("id"),"PaymentMANUAL_REVIEW",Map.of("resumeState",p.get("state")));}
 }
}
