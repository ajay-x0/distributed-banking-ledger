package dev.ajay.bank.fraud;
import java.util.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
@RestController
public class FraudController {
 private final JdbcTemplate db;private final long maximum;
 public FraudController(JdbcTemplate db,@Value("${FRAUD_MAX_MINOR:2000000}")long maximum){this.db=db;this.maximum=maximum;}
 public record Check(@NotNull UUID paymentId,@NotBlank String owner,@Min(1)long amountMinor){}
 @PostMapping("/internal/fraud/check") @Transactional Map<String,Object> check(@Valid @RequestBody Check r){
  boolean approved=r.amountMinor()<=maximum;
  db.update("INSERT INTO fraud_decision(payment_id,owner,amount_minor,approved,reason) VALUES (?,?,?,?,?) ON CONFLICT(payment_id) DO NOTHING",r.paymentId(),r.owner(),r.amountMinor(),approved,approved?"Within demo transfer threshold":"Demo transfer threshold exceeded");
  var result=db.queryForMap("SELECT * FROM fraud_decision WHERE payment_id=?",r.paymentId());
  if(!result.get("owner").equals(r.owner())||((Number)result.get("amount_minor")).longValue()!=r.amountMinor())throw new ResponseStatusException(HttpStatus.CONFLICT,"Fraud operation input mismatch");return result;
 }
}
