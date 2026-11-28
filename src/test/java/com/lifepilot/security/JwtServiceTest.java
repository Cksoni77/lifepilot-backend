package com.lifepilot.security;

import com.lifepilot.TestAppProperties;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class JwtServiceTest {
    private static final String SECRET = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void signsAndValidatesAccessTokenSubject() {
        JwtService jwtService = new JwtService(TestAppProperties.defaults());
        UUID userId = UUID.randomUUID();

        assertEquals(userId, jwtService.readUserId(jwtService.issueAccessToken(userId)));
    }

    @Test
    void accessTokenUsesConfiguredFifteenMinuteExpiry() {
        JwtService jwtService = new JwtService(TestAppProperties.create(
                SECRET, 15, 30, "0.30", "0.30", "0.20", "0.20"));
        String token = jwtService.issueAccessToken(UUID.randomUUID());
        var claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET)))
                .build().parseSignedClaims(token).getPayload();

        assertEquals(15 * 60_000L,
                claims.getExpiration().getTime() - claims.getIssuedAt().getTime());
    }

    @Test
    void rejectsExpiredAccessTokens() {
        JwtService jwtService = new JwtService(TestAppProperties.defaults());
        Instant expiredAt = Instant.now().minusSeconds(60);
        SecretKey key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET));
        String expiredToken = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .issuedAt(Date.from(expiredAt.minusSeconds(60)))
                .expiration(Date.from(expiredAt))
                .signWith(key)
                .compact();

        assertThrows(JwtException.class, () -> jwtService.readUserId(expiredToken));
    }

    @Test
    void rejectsTokensSignedByAnotherKeyAndMalformedTokens() {
        String otherSecret = Base64.getEncoder().encodeToString(new byte[] {
                1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
        });
        JwtService issuer = new JwtService(TestAppProperties.create(
                otherSecret, 15, 30, "0.30", "0.30", "0.20", "0.20"));
        JwtService verifier = new JwtService(TestAppProperties.defaults());
        String wrongSignature = issuer.issueAccessToken(UUID.randomUUID());

        assertThrows(JwtException.class, () -> verifier.readUserId(wrongSignature));
        assertThrows(JwtException.class, () -> verifier.readUserId("not-a-jwt"));
    }

    @Test
    void rejectsNonPositiveAccessTokenLifetime() {
        assertThrows(IllegalArgumentException.class,
                () -> new JwtService(TestAppProperties.create(
                        0, 30, "0.30", "0.30", "0.20", "0.20")));
    }

    @Test
    void rejectsWeakJwtSigningKey() {
        String weakSecret = Base64.getEncoder().encodeToString(new byte[16]);
        assertThrows(IllegalStateException.class,
                () -> new JwtService(TestAppProperties.create(
                        weakSecret, 15, 30, "0.30", "0.30", "0.20", "0.20")));
    }

    @Test
    void passwordsAreStoredAsVerifiableNonPlaintextHashes() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String password = "test-password-123";
        String hash = encoder.encode(password);

        assertNotEquals(password, hash);
        assertTrue(encoder.matches(password, hash));
    }
}
