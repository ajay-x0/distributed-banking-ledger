package dev.ajay.bank.payment;
import dev.ajay.bank.common.TransferCommand;
import java.net.URI;
import java.util.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
@RestController
public class PaymentController {
 private final PaymentService payments;
 public PaymentController(PaymentService payments){this.payments=payments;}
 @PostMapping("/api/payments") ResponseEntity<Map<String,Object>> create(@AuthenticationPrincipal Jwt jwt,@RequestHeader("Idempotency-Key")String key,@Valid @RequestBody TransferCommand t){var p=payments.create(jwt.getSubject(),key,t);return ResponseEntity.accepted().location(URI.create("/api/payments/"+p.get("id"))).body(p);}
 @GetMapping("/api/payments/{id}") Map<String,Object> get(@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt){return payments.get(id,jwt.getSubject());}
 @PostMapping("/api/admin/payments/{id}/resume") Map<String,Object> resume(@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt){return payments.resume(id,jwt.getSubject());}
}
