package dev.ajay.bank.common;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import static org.junit.jupiter.api.Assertions.*;
class LiteProfileTest {
 private StandardEnvironment load(boolean lite) {
  var environment = new StandardEnvironment();
  environment.getPropertySources().addFirst(new MapPropertySource("test-settings", lite
    ? Map.of("spring.profiles.active", "lite", "spring.config.import", "classpath:bank-common.yml")
    : Map.of("spring.config.import", "classpath:bank-common.yml")));
  ConfigDataEnvironmentPostProcessor.applyTo(environment);
  return environment;
 }
 @Test void liteProfileOverridesImportedDefaults() {
  var env=load(true);
  assertEquals("3",env.getProperty("spring.datasource.hikari.maximum-pool-size"));
  assertEquals("0",env.getProperty("spring.datasource.hikari.minimum-idle"));
  assertEquals("2",env.getProperty("spring.task.scheduling.pool.size"));
  assertEquals("false",env.getProperty("management.tracing.enabled"));
  assertEquals("false",env.getProperty("spring.kafka.listener.observation-enabled"));
  assertEquals("1048576",env.getProperty("spring.kafka.producer.properties.buffer.memory"));
  // Memory tuning must not weaken durability or change DB schema management.
  assertEquals("all",env.getProperty("spring.kafka.producer.acks"));
  assertEquals("true",env.getProperty("spring.kafka.producer.properties.enable.idempotence"));
  assertEquals("false",env.getProperty("spring.kafka.consumer.enable-auto-commit"));
  assertEquals("validate",env.getProperty("spring.jpa.hibernate.ddl-auto"));
 }
 @Test void regularProfileStillHasOriginalDefaults() {
  var env=load(false);
  assertEquals("10",env.getProperty("spring.datasource.hikari.maximum-pool-size"));
  assertEquals("3",env.getProperty("spring.task.scheduling.pool.size"));
  assertEquals("true",env.getProperty("spring.kafka.listener.observation-enabled"));
 }
}
