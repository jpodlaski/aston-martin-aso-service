package com.sanproject.aso_service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Creates and verifies JWTs (JSON Web Tokens) signed with HMAC-SHA256.
 * A JWT is a compact, signed string: header.payload.signature. Anyone can read the
 * payload, but only someone with the secret can forge a valid signature — that is how
 * the API trusts "this request is from user id X with role Y" without a server-side session store.
 */
@Service
public class JwtService {

    private final SecretKey secretKey;
    private final long expirationMs;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms}") long expirationMs) {
        // Secret must be long enough for HS256; override APP_JWT_SECRET in real deployments.
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String createToken(Long id, String role, String name) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        // subject = user id; custom claims carry role/name used by AuthSupport and the UI.
        return Jwts.builder()
                .subject(String.valueOf(id))
                .claim("role", role)
                .claim("name", name)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
    }

    public AuthUser parseToken(String token) {
        try {
            // verifyWith checks the signature and rejects expired/tampered tokens.
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            Long id = Long.valueOf(claims.getSubject());
            String role = claims.get("role", String.class);
            String name = claims.get("name", String.class);

            if (role == null || role.isBlank()) {
                throw new JwtException("Missing role claim");
            }

            return new AuthUser(id, role, name != null ? name : "");
        } catch (JwtException | IllegalArgumentException ex) {
            throw new JwtException("Invalid token", ex);
        }
    }
}
