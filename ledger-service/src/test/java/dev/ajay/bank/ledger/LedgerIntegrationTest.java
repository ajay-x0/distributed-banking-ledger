package dev.ajay.bank.ledger;
import dev.ajay.bank.common.*;
import java.util.concurrent.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
@Testcontainers
@SpringBootTest(properties={"bank.outbox-enabled=false","bank.workers-enabled=false","bank.jwt-secret=integration-test-secret-must-be-32-characters","bank.service-password=integration-test-only","management.tracing.enabled=false"})
class LedgerIntegrationTest {
 @Container static PostgreSQLContainer<?> pg=new PostgreSQLContainer<>("postgres:17.11");
 @DynamicPropertySource static void properties(DynamicPropertyRegistry r){r.add("spring.datasource.url",pg::getJdbcUrl);r.add("spring.datasource.username",pg::getUsername);r.add("spring.datasource.password",pg::getPassword);}
 @Autowired JdbcTemplate db;
 @Autowired LedgerService ledger;
 @Autowired TransactionTemplate tx;
 record Pair(UUID a,UUID b){}
 Pair pair(long source){UUID a=UUID.randomUUID(),b=UUID.randomUUID();db.update("INSERT INTO wallet(id,owner,currency,balance_minor) VALUES (?,'alice','INR',?),(?,'bob','INR',0)",a,source,b);return new Pair(a,b);}
 LedgerCommand command(UUID id,Pair p,long amount){return new LedgerCommand(id,"alice",new TransferCommand(p.a,p.b,amount,"INR"));}
 long balance(UUID id){return db.queryForObject("SELECT balance_minor FROM wallet WHERE id=?",Long.class,id);}
 @Test void repeatedReserveAndSettleMoveMoneyOnce(){var p=pair(10000);UUID id=UUID.randomUUID();ledger.reserve(command(id,p,1000));ledger.reserve(command(id,p,1000));ledger.finish(id,true);ledger.finish(id,true);assertEquals(9000,balance(p.a));assertEquals(1000,balance(p.b));assertEquals(1,db.queryForObject("SELECT count(*) FROM journal WHERE operation_id=?",Integer.class,id));}
 @Test void mismatchedRetryIsRejected(){var p=pair(10000);UUID id=UUID.randomUUID();ledger.reserve(command(id,p,1000));assertThrows(org.springframework.web.server.ResponseStatusException.class,()->ledger.reserve(command(id,p,2000)));assertEquals(1000,db.queryForObject("SELECT reserved_minor FROM wallet WHERE id=?",Long.class,p.a));}
 @Test void concurrentWithdrawalsCannotOverspend() throws Exception {
  var p=pair(10000);var pool=Executors.newFixedThreadPool(8);var jobs=new ArrayList<Callable<String>>();
  for(int i=0;i<20;i++)jobs.add(()->(String)ledger.reserve(command(UUID.randomUUID(),p,1000)).get("state"));
  try{long accepted=0;for(var f:pool.invokeAll(jobs))if(f.get().equals("RESERVED"))accepted++;assertEquals(10,accepted);assertEquals(10000,db.queryForObject("SELECT reserved_minor FROM wallet WHERE id=?",Long.class,p.a));assertEquals(10000,balance(p.a));}finally{pool.shutdownNow();}
 }
 @Test void concurrentDuplicateRequestsHaveOneEffect() throws Exception {
  var p=pair(10000);UUID id=UUID.randomUUID();var pool=Executors.newFixedThreadPool(4);var jobs=new ArrayList<Callable<Object>>();for(int i=0;i<8;i++)jobs.add(()->{ledger.reserve(command(id,p,1000));return ledger.finish(id,true);});
  try{for(var f:pool.invokeAll(jobs))f.get();assertEquals(9000,balance(p.a));assertEquals(1000,balance(p.b));}finally{pool.shutdownNow();}
 }
 @Test void oppositeDirectionPaymentsPreserveTotal() throws Exception {
  var p=pair(10000);db.update("UPDATE wallet SET balance_minor=10000 WHERE id=?",p.b);var pool=Executors.newFixedThreadPool(2);
  var one=new LedgerCommand(UUID.randomUUID(),"alice",new TransferCommand(p.a,p.b,100,"INR"));var two=new LedgerCommand(UUID.randomUUID(),"bob",new TransferCommand(p.b,p.a,200,"INR"));
  try{var jobs=List.<Callable<Object>>of(()->{ledger.reserve(one);return ledger.finish(one.paymentId(),true);},()->{ledger.reserve(two);return ledger.finish(two.paymentId(),true);});for(var f:pool.invokeAll(jobs))f.get();assertEquals(20000,balance(p.a)+balance(p.b));}finally{pool.shutdownNow();}
 }
 @Test void compensationReleasesWithoutPosting(){var p=pair(10000);UUID id=UUID.randomUUID();ledger.reserve(command(id,p,1000));ledger.finish(id,false);ledger.finish(id,false);assertEquals(10000,balance(p.a));assertEquals(0,db.queryForObject("SELECT reserved_minor FROM wallet WHERE id=?",Long.class,p.a));assertEquals(0,db.queryForObject("SELECT count(*) FROM journal WHERE operation_id=?",Integer.class,id));assertThrows(org.springframework.web.server.ResponseStatusException.class,()->ledger.finish(id,true));}
 @Test void rejectsUnbalancedOrEmptyJournalAtCommit(){
  assertThrows(org.springframework.dao.DataAccessException.class,()->tx.executeWithoutResult(s->{UUID j=UUID.randomUUID();db.update("INSERT INTO journal(id,kind,currency) VALUES (?,'OPENING','INR')",j);db.update("INSERT INTO journal_entry VALUES (?,?,1)",j,UUID.randomUUID());}));
  assertThrows(org.springframework.dao.DataAccessException.class,()->db.update("INSERT INTO journal(id,kind,currency) VALUES (?,'OPENING','INR')",UUID.randomUUID()));
 }
 @Test void journalCannotBeRewritten(){assertThrows(org.springframework.dao.DataAccessException.class,()->db.update("UPDATE journal_entry SET delta_minor=2 WHERE journal_id='10000000-0000-0000-0000-000000000001'"));}
 @Test void crashBeforeCommitRollsBackEveryEffect(){var p=pair(10000);UUID id=UUID.randomUUID();assertThrows(IllegalStateException.class,()->tx.executeWithoutResult(s->{ledger.reserve(command(id,p,1000));ledger.finish(id,true);throw new IllegalStateException("Simulated process failure before commit");}));assertEquals(10000,balance(p.a));assertEquals(0,balance(p.b));assertEquals(0,db.queryForObject("SELECT count(*) FROM ledger_operation WHERE id=?",Integer.class,id));assertEquals(0,db.queryForObject("SELECT count(*) FROM outbox WHERE aggregate_id=?",Integer.class,id));}
 @Test void sourceOwnershipIsEnforced(){var p=pair(10000);assertThrows(org.springframework.web.server.ResponseStatusException.class,()->ledger.reserve(new LedgerCommand(UUID.randomUUID(),"mallory",new TransferCommand(p.a,p.b,1,"INR"))));assertEquals(10000,balance(p.a));}
}
