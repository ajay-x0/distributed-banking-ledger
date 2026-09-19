package dev.ajay.bank.common;
import java.time.Duration;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
@Configuration
public class Clients {
 @Bean RestClient internalClient(RestClient.Builder builder,@Value("${bank.service-password}")String password){
  var factory=new SimpleClientHttpRequestFactory();factory.setConnectTimeout(Duration.ofSeconds(2));factory.setReadTimeout(Duration.ofSeconds(3));
  return builder.requestFactory(factory).defaultHeaders(h->h.setBasicAuth("service",password)).build();
 }
}
