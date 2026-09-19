package dev.ajay.bank.common;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;
import io.micrometer.core.instrument.MeterRegistry;
@Component @ConditionalOnProperty(name="bank.outbox-enabled",havingValue="true")
public class OutboxRelay {
 private final JdbcTemplate db;private final KafkaTemplate<String,String> kafka;private final TransactionTemplate tx;private final MeterRegistry meters;
 public OutboxRelay(JdbcTemplate db,KafkaTemplate<String,String> kafka,TransactionTemplate tx,MeterRegistry meters){this.db=db;this.kafka=kafka;this.tx=tx;this.meters=meters;}
 @Scheduled(fixedDelayString="${bank.relay-delay:500}") public void publish(){
  tx.executeWithoutResult(status->{
   var rows=db.queryForList("SELECT * FROM outbox WHERE published_at IS NULL AND next_attempt_at<=now() ORDER BY created_at LIMIT 20 FOR UPDATE SKIP LOCKED");
   for(var row:rows)try{
    kafka.send((String)row.get("topic"),row.get("aggregate_id").toString(),(String)row.get("payload")).get(5,TimeUnit.SECONDS);
    db.update("UPDATE outbox SET published_at=now() WHERE id=?",row.get("id"));meters.counter("bank.outbox.published").increment();
   }catch(Exception e){if(e instanceof InterruptedException)Thread.currentThread().interrupt();int attempt=((Number)row.get("attempts")).intValue()+1;
    db.update("UPDATE outbox SET attempts=?,next_attempt_at=now()+(? * interval '1 second') WHERE id=?",attempt,MoneyRules.retrySeconds(attempt),row.get("id"));meters.counter("bank.outbox.retries").increment();
   }
  });
 }
}
