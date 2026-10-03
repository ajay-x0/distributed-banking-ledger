package dev.ajay.bank.gateway;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

class DemoTokenControllerTest {
    private static final String SECRET = "0123456789abcdef0123456789abcdef";
    private final ObjectMapper json = new ObjectMapper();
    private final DemoTokenController controller = new DemoTokenController(json, SECRET);

    @Test
    void issuesUserTokenWithoutReturningSigningSecret() throws Exception {
        var response = controller.issue("alice");
        var body = response.getBody();
        assertNotNull(body);
        assertEquals("alice", body.get("subject"));
        assertEquals("payments", body.get("scope"));
        assertFalse(body.containsValue(SECRET));

        String token = String.valueOf(body.get("token"));
        String payload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
        var claims = json.readTree(payload);
        assertEquals("bank-demo", claims.path("iss").asText());
        assertEquals("bank-api", claims.path("aud").get(0).asText());
        assertEquals("alice", claims.path("sub").asText());
        assertEquals("payments", claims.path("scope").asText());
    }

    @Test
    void issuesAdminScopeOnlyForAdmin() {
        assertEquals("admin", controller.issue("admin").getBody().get("scope"));
    }

    @Test
    void rejectsUnknownIdentity() {
        assertThrows(ResponseStatusException.class, () -> controller.issue("attacker"));
    }
}
