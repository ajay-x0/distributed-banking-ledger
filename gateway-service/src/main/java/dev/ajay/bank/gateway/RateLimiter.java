package dev.ajay.bank.gateway;
import java.util.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
@Component
public class RateLimiter {
 private final StringRedisTemplate redis;private final long limit;
 private final DefaultRedisScript<Long> script=new DefaultRedisScript<>("local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('EXPIRE',KEYS[1],60) end; return n",Long.class);
 public RateLimiter(StringRedisTemplate redis,@Value("${RATE_LIMIT_PER_MINUTE:120}")long limit){this.redis=redis;this.limit=limit;}
 public void check(String subject){
  Long n;
  try{n=redis.execute(script,List.of("bank:rate:"+subject));}catch(org.springframework.dao.DataAccessException e){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Rate limiter unavailable; retry later");}
  if(n==null)throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Rate limiter unavailable");
  if(n>limit)throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Request limit reached; wait 60 seconds");
 }
}
