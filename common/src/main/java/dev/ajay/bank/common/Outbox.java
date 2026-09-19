package dev.ajay.bank.common;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
@Component
public class Outbox {
 private final JdbcTemplate db; private final ObjectMapper json;
 public Outbox(JdbcTemplate db,ObjectMapper json){this.db=db;this.json=json;}
 /** Caller must already be in the business transaction. */
 public void append(UUID aggregate,String type,Map<String,Object> body){
  try {UUID id=UUID.randomUUID();var event=new LinkedHashMap<String,Object>();event.put("eventId",id);event.put("aggregateId",aggregate);event.put("type",type);event.put("version",1);event.put("data",body);
   db.update("INSERT INTO outbox(id,aggregate_id,topic,payload) VALUES (?,?,?,?)",id,aggregate,"bank.events",json.writeValueAsString(event));
  }catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException(e);}
 }
}
