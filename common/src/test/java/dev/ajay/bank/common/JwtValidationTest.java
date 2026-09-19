package dev.ajay.bank.common;
import java.time.Instant;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import static org.junit.jupiter.api.Assertions.*;
class JwtValidationTest {
 static final String SECRET="test-signing-secret-with-more-than-32-characters";
 String token(String issuer,String audience,Instant expiry){var encoder=new NimbusJwtEncoder(new ImmutableSecret<>(new SecretKeySpec(SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8),"HmacSHA256")));return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),JwtClaimsSet.builder().issuer(issuer).audience(List.of(audience)).subject("alice").expiresAt(expiry).build())).getTokenValue();}
 @Test void validTokenAccepted(){assertEquals("alice",new SecurityConfig().jwtDecoder(SECRET).decode(token("bank-demo","bank-api",Instant.now().plusSeconds(600))).getSubject());}
 @Test void incorrectIssuerRejected(){assertThrows(JwtException.class,()->new SecurityConfig().jwtDecoder(SECRET).decode(token("other","bank-api",Instant.now().plusSeconds(600))));}
 @Test void incorrectAudienceRejected(){assertThrows(JwtException.class,()->new SecurityConfig().jwtDecoder(SECRET).decode(token("bank-demo","other",Instant.now().plusSeconds(600))));}
 @Test void expiredTokenRejected(){assertThrows(JwtException.class,()->new SecurityConfig().jwtDecoder(SECRET).decode(token("bank-demo","bank-api",Instant.now().minusSeconds(600))));}
 @Test void wrongSigningKeyRejected(){assertThrows(JwtException.class,()->new SecurityConfig().jwtDecoder("different-signing-secret-at-least-32-characters").decode(token("bank-demo","bank-api",Instant.now().plusSeconds(600))));}
}
