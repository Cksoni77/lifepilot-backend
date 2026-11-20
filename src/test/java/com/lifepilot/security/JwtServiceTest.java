package com.lifepilot.security;

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
        JwtService jwtService = new JwtService(SECRET, 15);
        UUID userId = UUID.randomUUID();

        assertEquals(userId, jwtService.readUserId(jwtService.issueAccessToken(userId)));
    }

    @Test
    void rejectsExpiredAccessTokens() {
        JwtService jwtService = new JwtService(SECRET, 15);
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
        JwtService issuer = new JwtService(otherSecret, 15);
        JwtService verifier = new JwtService(SECRET, 15);
        String wrongSignature = issuer.issueAccessToken(UUID.randomUUID());

        assertThrows(JwtException.class, () -> verifier.readUserId(wrongSignature));
        assertThrows(JwtException.class, () -> verifier.readUserId("not-a-jwt"));
    }

    @Test
    void rejectsNonPositiveAccessTokenLifetime() {
        assertThrows(IllegalArgumentException.class, () -> new JwtService(SECRET, 0));
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
