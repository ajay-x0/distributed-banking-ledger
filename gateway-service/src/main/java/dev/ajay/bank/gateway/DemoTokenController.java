package dev.ajay.bank.gateway;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Set;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * Local-demo identity switcher. It is disabled unless DEMO_AUTH_ENABLED=true.
 * The signing secret remains on the server and is never shipped in browser code.
 */
@RestController
@ConditionalOnProperty(name = "bank.demo-auth-enabled", havingValue = "true")
public class DemoTokenController {
    private static final Set<String> USERS = Set.of("alice", "bob", "charlie", "admin");
    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();

    private final ObjectMapper json;
    private final byte[] secret;

    public DemoTokenController(ObjectMapper json, @Value("${bank.jwt-secret}") String secret) {
        if (secret.length() < 32) {
            throw new IllegalArgumentException("JWT_SECRET needs at least 32 characters");
        }
        this.json = json;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    @GetMapping("/demo/token/{subject}")
    ResponseEntity<Map<String, Object>> issue(@PathVariable String subject) {
        if (!USERS.contains(subject)) {
            throw new ResponseStatusException(NOT_FOUND, "Unknown demo identity");
        }

        long issuedAt = Instant.now().getEpochSecond();
        long expiresAt = issuedAt + 15 * 60;
        String scope = subject.equals("admin") ? "admin" : "payments";
        String token = sign(Map.of(
            "iss", "bank-demo",
            "aud", new String[]{"bank-api"},
            "sub", subject,
            "scope", scope,
            "iat", issuedAt,
            "exp", expiresAt
        ));

        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(Map.of(
                "token", token,
                "subject", subject,
                "scope", scope,
                "expiresAt", expiresAt
            ));
    }

    private String sign(Map<String, Object> claims) {
        try {
            String header = encode(json.writeValueAsBytes(Map.of("alg", "HS256", "typ", "JWT")));
            String payload = encode(json.writeValueAsBytes(claims));
            String body = header + "." + payload;
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return body + "." + encode(hmac.doFinal(body.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception error) {
            throw new IllegalStateException("Could not issue demo token", error);
        }
    }

    private static String encode(byte[] value) {
        return BASE64_URL.encodeToString(value);
    }
}
