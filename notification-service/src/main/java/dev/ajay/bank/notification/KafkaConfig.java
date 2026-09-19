package dev.ajay.bank.notification;
import org.springframework.context.annotation.*;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.*;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.apache.kafka.common.TopicPartition;
@Configuration
public class KafkaConfig {
 @Bean org.springframework.kafka.core.KafkaAdmin.NewTopics topics(){return new org.springframework.kafka.core.KafkaAdmin.NewTopics(TopicBuilder.name("bank.events").partitions(3).replicas(1).build(),TopicBuilder.name("bank.events.DLT").partitions(3).replicas(1).build());}
 @Bean DefaultErrorHandler errorHandler(KafkaTemplate<String,String> template){
  var recoverer=new DeadLetterPublishingRecoverer(template,(record,ex)->new TopicPartition(record.topic()+".DLT",record.partition()));
  recoverer.setFailIfSendResultIsError(true);
  var backoff=new ExponentialBackOffWithMaxRetries(4);backoff.setInitialInterval(500);backoff.setMultiplier(2);backoff.setMaxInterval(4000);
  return new DefaultErrorHandler(recoverer,backoff);
 }
}
