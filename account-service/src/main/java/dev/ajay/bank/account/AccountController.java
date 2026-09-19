package dev.ajay.bank.account;
import java.util.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
@RestController
public class AccountController {
 private final AccountService accounts;public AccountController(AccountService accounts){this.accounts=accounts;}
 public record Rename(@Min(0)long version,@NotBlank @Size(max=100)String displayName){}
 @GetMapping("/api/accounts/{id}") AccountProfile get(@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt){return accounts.get(id,jwt.getSubject());}
 @PutMapping("/api/accounts/{id}") AccountProfile rename(@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody Rename r){return accounts.rename(id,jwt.getSubject(),r.version(),r.displayName());}
 @GetMapping("/internal/accounts/{id}/owner/{owner}") Map<String,Object> ownership(@PathVariable UUID id,@PathVariable String owner){accounts.get(id,owner);return Map.of("owned",true);}
}
