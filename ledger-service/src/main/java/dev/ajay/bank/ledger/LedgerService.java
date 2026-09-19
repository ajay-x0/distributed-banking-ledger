package dev.ajay.bank.ledger;
import dev.ajay.bank.common.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import io.micrometer.core.instrument.MeterRegistry;
@Service
public class LedgerService {
 private final WalletRepository wallets;private final JdbcTemplate db;private final Outbox outbox;private final MeterRegistry meters;
 public LedgerService(WalletRepository wallets,JdbcTemplate db,Outbox outbox,MeterRegistry meters){this.wallets=wallets;this.db=db;this.outbox=outbox;this.meters=meters;}
 private void lock(UUID id){db.execute("SET LOCAL lock_timeout='2s'");db.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",id.toString());}
 private Map<String,Object> operation(UUID id){return db.queryForMap("SELECT * FROM ledger_operation WHERE id=?",id);}
 @Transactional public Map<String,Object> reserve(LedgerCommand command){
  var t=command.transfer();t.validate();lock(command.paymentId());
  var existing=db.queryForList("SELECT * FROM ledger_operation WHERE id=?",command.paymentId());
  if(!existing.isEmpty()){
   var op=existing.get(0);
   if(!op.get("owner").equals(command.owner())||!op.get("source").equals(t.source())||!op.get("destination").equals(t.destination())||((Number)op.get("amount_minor")).longValue()!=t.amountMinor()||!op.get("currency").equals(t.currency()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Operation ID reused with different input");
   return op;
  }
  var locked=wallets.lockAll(List.of(t.source(),t.destination()));
  if(locked.size()!=2)throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"Account does not exist");
  Wallet source=locked.stream().filter(w->w.id.equals(t.source())).findFirst().orElseThrow();
  if(!source.owner.equals(command.owner()))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Source account ownership mismatch");
  if(locked.stream().anyMatch(w->!w.currency.equals(t.currency())))throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"Currency mismatch");
  String state="REJECTED";
  if(MoneyRules.available(source.balance,source.reserved)>=t.amountMinor()) {source.reserved=MoneyRules.reserve(source.balance,source.reserved,t.amountMinor());wallets.flush();state="RESERVED";}
  db.update("INSERT INTO ledger_operation(id,owner,source,destination,amount_minor,currency,state) VALUES (?,?,?,?,?,?,?)",command.paymentId(),command.owner(),t.source(),t.destination(),t.amountMinor(),t.currency(),state);
  outbox.append(command.paymentId(),"Ledger"+state,Map.of("state",state));return operation(command.paymentId());
 }
 @Transactional public Map<String,Object> finish(UUID id,boolean settle){
  lock(id);var rows=db.queryForList("SELECT * FROM ledger_operation WHERE id=?",id);
  if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Unknown operation");var op=rows.get(0);String state=(String)op.get("state");
  if((settle&&state.equals("POSTED"))||(!settle&&state.equals("RELEASED")))return op;
  if(!state.equals("RESERVED"))throw new ResponseStatusException(HttpStatus.CONFLICT,"Operation already terminal: "+state);
  UUID src=(UUID)op.get("source"),dst=(UUID)op.get("destination");long amount=((Number)op.get("amount_minor")).longValue();
  var locked=wallets.lockAll(List.of(src,dst));Wallet source=locked.stream().filter(w->w.id.equals(src)).findFirst().orElseThrow();Wallet dest=locked.stream().filter(w->w.id.equals(dst)).findFirst().orElseThrow();
  source.reserved=Math.subtractExact(source.reserved,amount);
  if(settle){
   source.balance=MoneyRules.debit(source.balance,amount);dest.balance=MoneyRules.credit(dest.balance,amount);
   UUID journal=UUID.randomUUID();db.update("INSERT INTO journal(id,operation_id,kind,currency) VALUES (?,?,'TRANSFER','INR')",journal,id);
   db.update("INSERT INTO journal_entry(journal_id,account_id,delta_minor) VALUES (?,?,?),(?,?,?)",journal,src,-amount,journal,dst,amount);
  }
  wallets.flush();String next=settle?"POSTED":"RELEASED";db.update("UPDATE ledger_operation SET state=? WHERE id=?",next,id);
  outbox.append(id,"Ledger"+next,Map.of("source",src,"destination",dst,"amountMinor",amount,"currency","INR"));meters.counter("bank.ledger.operations","state",next).increment();return operation(id);
 }
 @Transactional(readOnly=true) public Map<String,Object> balance(UUID id,String owner){
  Wallet w=wallets.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Unknown account"));
  if(!w.owner.equals(owner))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Not your account");
  return Map.of("accountId",id,"currency",w.currency,"balanceMinor",w.balance,"reservedMinor",w.reserved,"availableMinor",MoneyRules.available(w.balance,w.reserved),"version",w.version);
 }
}
