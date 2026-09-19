package dev.ajay.bank.payment;
import dev.ajay.bank.common.*;
import java.util.concurrent.*;
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
class PaymentIntegrationTest {
 @Container static PostgreSQLContainer<?> pg=new PostgreSQLContainer<>("postgres:17.11");
 @DynamicPropertySource static void properties(DynamicPropertyRegistry r){r.add("spring.datasource.url",pg::getJdbcUrl);r.add("spring.datasource.username",pg::getUsername);r.add("spring.datasource.password",pg::getPassword);}
 @Autowired JdbcTemplate db;
 @Autowired PaymentService service;
 TransferCommand transfer(){return new TransferCommand(UUID.randomUUID(),UUID.randomUUID(),1000000,"INR");}
 @Test void idempotencyReturnsSamePayment(){String key=UUID.randomUUID().toString();var t=transfer();var first=service.create("alice",key,t);assertEquals(first.get("id"),service.create("alice",key,t).get("id"));assertEquals(1,db.queryForObject("SELECT count(*) FROM outbox WHERE aggregate_id=?",Integer.class,first.get("id")));}
 @Test void changedPayloadConflicts(){String key=UUID.randomUUID().toString();service.create("alice",key,transfer());assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.create("alice",key,transfer()));}
 @Test void keysAreScopedToOwner(){String key=UUID.randomUUID().toString();var t=transfer();assertNotEquals(service.create("alice",key,t).get("id"),service.create("bob",key,t).get("id"));}
 @Test void ownerCannotReadOthersPayment(){UUID id=(UUID)service.create("alice",UUID.randomUUID().toString(),transfer()).get("id");assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.get(id,"bob"));}
 @Test void concurrentCreateReturnsOneId() throws Exception {var pool=Executors.newFixedThreadPool(8);var t=transfer();String key=UUID.randomUUID().toString();var jobs=new ArrayList<Callable<Object>>();for(int i=0;i<20;i++)jobs.add(()->service.create("alice",key,t).get("id"));try{var ids=new HashSet<>();for(var f:pool.invokeAll(jobs))ids.add(f.get());assertEquals(1,ids.size());}finally{pool.shutdownNow();}}
 @Test void resumePreservesLastSafeStepAndIsAudited(){UUID id=(UUID)service.create("alice",UUID.randomUUID().toString(),transfer()).get("id");db.update("UPDATE payment SET state='MANUAL_REVIEW',resume_state='APPROVED',attempts=8 WHERE id=?",id);assertEquals("APPROVED",service.resume(id,"admin").get("state"));assertEquals(1,db.queryForObject("SELECT count(*) FROM payment_audit WHERE payment_id=? AND actor='admin'",Integer.class,id));}
}
