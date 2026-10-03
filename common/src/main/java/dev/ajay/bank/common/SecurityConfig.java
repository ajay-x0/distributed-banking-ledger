package dev.ajay.bank.common;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;
@Configuration
public class SecurityConfig {
 @Bean JwtDecoder jwtDecoder(@Value("${bank.jwt-secret}") String secret) {
  if(secret.length()<32) throw new IllegalArgumentException("JWT_SECRET needs at least 32 characters");
  var decoder=NimbusJwtDecoder.withSecretKey(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256")).macAlgorithm(MacAlgorithm.HS256).build();
  decoder.setJwtValidator(new org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer("bank-demo"),new JwtClaimValidator<java.util.List<String>>("aud",a->a!=null&&a.contains("bank-api"))));
  return decoder;
 }
 @Bean UserDetailsService users(@Value("${bank.service-password}") String password) {
  return new InMemoryUserDetailsManager(User.withUsername("service").password(new BCryptPasswordEncoder().encode(password)).roles("INTERNAL").build());
 }
 @Bean org.springframework.security.crypto.password.PasswordEncoder encoder(){return new BCryptPasswordEncoder();}
 @Bean @Order(1) SecurityFilterChain internal(HttpSecurity http) throws Exception {
  return http.securityMatcher("/internal/**").csrf(c->c.disable()).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS)).authorizeHttpRequests(a->a.anyRequest().hasRole("INTERNAL")).httpBasic(Customizer.withDefaults()).build();
 }
 @Bean @Order(2) SecurityFilterChain publicApi(HttpSecurity http) throws Exception {
  return http.csrf(c->c.disable()).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS)).authorizeHttpRequests(a->a.requestMatchers("/actuator/health/**","/actuator/prometheus","/actuator/info","/demo/token/**").permitAll().requestMatchers("/api/admin/**").hasAuthority("SCOPE_admin").anyRequest().authenticated()).oauth2ResourceServer(o->o.jwt(Customizer.withDefaults())).build();
 }
}
