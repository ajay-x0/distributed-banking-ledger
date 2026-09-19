package dev.ajay.bank.notification;
import com.fasterxml.jackson.databind.ObjectMapper;
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
@SpringBootTest(properties={"bank.outbox-enabled=false","bank.workers-enabled=false","bank.jwt-secret=integration-test-secret-must-be-32-characters","bank.service-password=integration-test-only","management.tracing.enabled=false","spring.kafka.listener.auto-startup=false","spring.kafka.admin.auto-create=false"})
class NotificationIntegrationTest {
 @Container static PostgreSQLContainer<?> pg=new PostgreSQLContainer<>("postgres:17.11");
 @DynamicPropertySource static void properties(DynamicPropertyRegistry r){r.add("spring.datasource.url",pg::getJdbcUrl);r.add("spring.datasource.username",pg::getUsername);r.add("spring.datasource.password",pg::getPassword);}
 @Autowired JdbcTemplate db;
 @Autowired EventConsumer consumer;
 @Autowired ObjectMapper json;
 String event(UUID id,UUID payment)throws Exception{return json.writeValueAsString(Map.of("eventId",id,"aggregateId",payment,"type","PaymentCOMPLETED","version",1));}
 @Test void duplicateDeliveryAndNewEventIdCannotDoubleCount() throws Exception {UUID payment=UUID.randomUUID();String e=event(UUID.randomUUID(),payment);consumer.consume(e);consumer.consume(e);consumer.consume(event(UUID.randomUUID(),payment));assertEquals(1,db.queryForObject("SELECT count(*) FROM notification WHERE payment_id=?",Integer.class,payment));assertEquals(1,db.queryForObject("SELECT value FROM analytics_counter WHERE name='PaymentCOMPLETED'",Integer.class));}
 @Test void poisonMessageDoesNotCreateInboxRecord(){assertThrows(Exception.class,()->consumer.consume("bad-json"));assertThrows(Exception.class,()->consumer.consume("{\"version\":99}"));}
}
