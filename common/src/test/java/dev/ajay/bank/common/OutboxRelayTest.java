package dev.ajay.bank.common;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class OutboxRelayTest {
 @Test void failedBrokerAcknowledgementLeavesEventPending(){
  JdbcTemplate db=mock(JdbcTemplate.class);KafkaTemplate<String,String> kafka=mock(KafkaTemplate.class);TransactionTemplate tx=mock(TransactionTemplate.class);UUID event=UUID.randomUUID(),aggregate=UUID.randomUUID();
  doAnswer(call->{((java.util.function.Consumer<TransactionStatus>)call.getArgument(0)).accept(mock(TransactionStatus.class));return null;}).when(tx).executeWithoutResult(any());
  when(db.queryForList(anyString())).thenReturn(List.of(Map.of("id",event,"aggregate_id",aggregate,"topic","bank.events","payload","{}","attempts",0)));
  when(kafka.send("bank.events",aggregate.toString(),"{}")).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")));
  new OutboxRelay(db,kafka,tx,new SimpleMeterRegistry()).publish();
  verify(db).update(startsWith("UPDATE outbox SET attempts=?"),eq(1),eq(2L),eq(event));
  verify(db,never()).update(startsWith("UPDATE outbox SET published_at"),any(Object.class));
 }
 @Test void brokerAcknowledgementMarksEventPublished(){
  JdbcTemplate db=mock(JdbcTemplate.class);KafkaTemplate<String,String> kafka=mock(KafkaTemplate.class);TransactionTemplate tx=mock(TransactionTemplate.class);UUID event=UUID.randomUUID(),aggregate=UUID.randomUUID();
  doAnswer(call->{((java.util.function.Consumer<TransactionStatus>)call.getArgument(0)).accept(mock(TransactionStatus.class));return null;}).when(tx).executeWithoutResult(any());
  when(db.queryForList(anyString())).thenReturn(List.of(Map.of("id",event,"aggregate_id",aggregate,"topic","bank.events","payload","{}","attempts",0)));
  when(kafka.send("bank.events",aggregate.toString(),"{}")).thenReturn(CompletableFuture.completedFuture(null));
  new OutboxRelay(db,kafka,tx,new SimpleMeterRegistry()).publish();
  verify(db).update(startsWith("UPDATE outbox SET published_at"),eq(event));
 }
}
