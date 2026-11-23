package com.lifepilot.security;

import com.lifepilot.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final SecretKey key;
    private final Duration accessTokenLifetime;

    public JwtService(AppProperties properties) {
        if (properties.jwt().accessTokenMinutes() <= 0) {
            throw new IllegalArgumentException("JWT access-token lifetime must be positive");
        }
        try {
            this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.jwt().secret()));
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "app.jwt.secret must be valid base64 encoding of at least 32 bytes");
        }
        this.accessTokenLifetime = Duration.ofMinutes(properties.jwt().accessTokenMinutes());
    }

    public String issueAccessToken(UUID userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenLifetime)))
                .signWith(key)
                .compact();
    }

    public UUID readUserId(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload();
        return UUID.fromString(claims.getSubject());
    }
}
