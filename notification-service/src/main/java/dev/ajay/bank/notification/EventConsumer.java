package dev.ajay.bank.notification;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
@Component
public class EventConsumer {
 private final JdbcTemplate db;private final ObjectMapper json;
 public EventConsumer(JdbcTemplate db,ObjectMapper json){this.db=db;this.json=json;}
 @KafkaListener(topics="bank.events") @Transactional public void consume(String value) throws Exception {
  var event=json.readTree(value);
  if(event.path("version").asInt()!=1||!event.hasNonNull("type")||!event.hasNonNull("eventId")||!event.hasNonNull("aggregateId"))throw new IllegalArgumentException("Unsupported event envelope");
  UUID id=UUID.fromString(event.path("eventId").asText()),payment=UUID.fromString(event.path("aggregateId").asText());
  if(db.update("INSERT INTO processed_event(event_id) VALUES (?) ON CONFLICT DO NOTHING",id)==0)return;
  String type=event.path("type").asText();
  if(type.equals("PaymentCOMPLETED")||type.equals("PaymentREJECTED")){
   String state=type.substring("Payment".length());
   int inserted=db.update("INSERT INTO notification(payment_id,state) VALUES (?,?) ON CONFLICT DO NOTHING",payment,state);
   if(inserted==1)db.update("INSERT INTO analytics_counter(name,value) VALUES (?,1) ON CONFLICT(name) DO UPDATE SET value=analytics_counter.value+1",type);
  }
 }
}
