package dev.ajay.bank.ledger;
import dev.ajay.bank.common.LedgerCommand;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
@RestController
public class LedgerController {
 private final LedgerService ledger;private final org.springframework.jdbc.core.JdbcTemplate db;
 public LedgerController(LedgerService ledger,org.springframework.jdbc.core.JdbcTemplate db){this.ledger=ledger;this.db=db;}
 @PostMapping("/internal/ledger/reserve") Map<String,Object> reserve(@RequestBody LedgerCommand command){return ledger.reserve(command);}
 @PostMapping("/internal/ledger/{id}/settle") Map<String,Object> settle(@PathVariable UUID id){return ledger.finish(id,true);}
 @PostMapping("/internal/ledger/{id}/release") Map<String,Object> release(@PathVariable UUID id){return ledger.finish(id,false);}
 @GetMapping("/internal/ledger/{id}") Map<String,Object> operation(@PathVariable UUID id){return db.queryForMap("SELECT * FROM ledger_operation WHERE id=?",id);}
 @GetMapping("/api/accounts/{id}/balance") Map<String,Object> balance(@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt){return ledger.balance(id,jwt.getSubject());}
 @GetMapping("/api/accounts/{id}/entries") List<Map<String,Object>> entries(@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue="0") int offset){ledger.balance(id,jwt.getSubject());return db.queryForList("SELECT e.*,j.kind,j.created_at FROM journal_entry e JOIN journal j ON j.id=e.journal_id WHERE account_id=? ORDER BY j.created_at,j.id LIMIT 100 OFFSET ?",id,Math.max(0,offset));}
 @GetMapping("/api/admin/reconciliation") Map<String,Object> reconciliation(){
  return Map.of("unbalancedJournals",db.queryForList("SELECT journal_id FROM journal_entry GROUP BY journal_id HAVING sum(delta_minor)<>0 OR count(*)<>2"),"balanceMismatches",db.queryForList("SELECT w.id,w.balance_minor,coalesce(sum(e.delta_minor),0) AS derived FROM wallet w LEFT JOIN journal_entry e ON e.account_id=w.id GROUP BY w.id HAVING w.balance_minor<>coalesce(sum(e.delta_minor),0)"),"reservationMismatches",db.queryForList("SELECT w.id,w.reserved_minor,coalesce(sum(o.amount_minor),0) AS derived FROM wallet w LEFT JOIN ledger_operation o ON o.source=w.id AND o.state='RESERVED' GROUP BY w.id HAVING w.reserved_minor<>coalesce(sum(o.amount_minor),0)"));
 }
}
