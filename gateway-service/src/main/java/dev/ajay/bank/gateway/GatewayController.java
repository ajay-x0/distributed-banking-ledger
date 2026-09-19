package dev.ajay.bank.gateway;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import java.time.Duration;
@RestController
public class GatewayController {
 private final RestClient client;private final RateLimiter limits;private final String payments,accounts,ledger,notifications;
 public GatewayController(RestClient.Builder builder,RateLimiter limits,@Value("${bank.payment-url}")String payments,@Value("${bank.account-url}")String accounts,@Value("${bank.ledger-url}")String ledger,@Value("${bank.notification-url}")String notifications){
  var f=new SimpleClientHttpRequestFactory();f.setConnectTimeout(Duration.ofSeconds(2));f.setReadTimeout(Duration.ofSeconds(5));client=builder.requestFactory(f).build();this.limits=limits;this.payments=payments;this.accounts=accounts;this.ledger=ledger;this.notifications=notifications;
 }
 @RequestMapping("/api/**") ResponseEntity<byte[]> forward(HttpServletRequest req,@AuthenticationPrincipal Jwt jwt,@RequestBody(required=false)byte[] body){
  limits.check(jwt.getSubject());String path=req.getRequestURI();String target;
  if(path.matches("/api/payments(?:/[0-9a-fA-F-]{36})?")||path.matches("/api/admin/payments/[0-9a-fA-F-]{36}/resume"))target=payments;
  else if(path.matches("/api/accounts/[0-9a-fA-F-]{36}/(balance|entries)")||path.equals("/api/admin/reconciliation"))target=ledger;
  else if(path.matches("/api/accounts/[0-9a-fA-F-]{36}"))target=accounts;
  else if(path.equals("/api/admin/analytics"))target=notifications;
  else throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Unknown API route");
  try{
   var call=client.method(HttpMethod.valueOf(req.getMethod())).uri(java.net.URI.create(target+path+(req.getQueryString()==null?"":"?"+req.getQueryString()))).headers(h->{h.setBearerAuth(jwt.getTokenValue());if(req.getHeader("Content-Type")!=null)h.set("Content-Type",req.getHeader("Content-Type"));if(req.getHeader("Idempotency-Key")!=null)h.set("Idempotency-Key",req.getHeader("Idempotency-Key"));});
   if(body!=null)call.body(body);
   return call.exchange((request,response)->{var headers=new HttpHeaders();for(String name:java.util.List.of("Content-Type","Location","Retry-After")){if(response.getHeaders().containsKey(name))headers.put(name,response.getHeaders().get(name));}return new ResponseEntity<>(response.getBody().readAllBytes(),headers,response.getStatusCode());});
  }catch(ResourceAccessException e){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Upstream unavailable; retry with the same idempotency key");}
 }
}
