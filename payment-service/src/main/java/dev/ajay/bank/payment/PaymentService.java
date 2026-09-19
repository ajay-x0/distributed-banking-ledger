package dev.ajay.bank.payment;
import dev.ajay.bank.common.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
@Service
public class PaymentService {
 private final JdbcTemplate db;private final Outbox outbox;
 public PaymentService(JdbcTemplate db,Outbox outbox){this.db=db;this.outbox=outbox;}
 @Transactional public Map<String,Object> create(String owner,String key,TransferCommand t){
  t.validate();if(key==null||!key.matches("[A-Za-z0-9_.:-]{1,100}"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Idempotency-Key must contain 1–100 letters, digits, dots, underscores, colons or hyphens");
  UUID id=UUID.randomUUID();int inserted=db.update("INSERT INTO payment(id,owner,idempotency_key,source,destination,amount_minor,currency) VALUES (?,?,?,?,?,?,?) ON CONFLICT(owner,idempotency_key) DO NOTHING",id,owner,key,t.source(),t.destination(),t.amountMinor(),t.currency());
  var row=db.queryForMap("SELECT * FROM payment WHERE owner=? AND idempotency_key=?",owner,key);
  if(!row.get("source").equals(t.source())||!row.get("destination").equals(t.destination())||((Number)row.get("amount_minor")).longValue()!=t.amountMinor()||!row.get("currency").equals(t.currency()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Idempotency-Key already used for different transfer details");
  if(inserted==1)outbox.append(id,"PaymentAccepted",Map.of("owner",owner));return row;
 }
 public Map<String,Object> get(UUID id,String owner){var rows=db.queryForList("SELECT * FROM payment WHERE id=? AND owner=?",id,owner);if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Payment not found");return rows.get(0);}
 @Transactional public Map<String,Object> resume(UUID id,String actor){
  var rows=db.queryForList("SELECT * FROM payment WHERE id=? FOR UPDATE",id);if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Payment not found");var p=rows.get(0);
  if(!"MANUAL_REVIEW".equals(p.get("state")))throw new ResponseStatusException(HttpStatus.CONFLICT,"Only MANUAL_REVIEW payments can be resumed");
  db.update("UPDATE payment SET state=resume_state,resume_state=NULL,attempts=0,next_attempt_at=now(),updated_at=now() WHERE id=?",id);
  db.update("INSERT INTO payment_audit(payment_id,actor,action) VALUES (?,?,'RESUME')",id,actor);return db.queryForMap("SELECT * FROM payment WHERE id=?",id);
 }
}
