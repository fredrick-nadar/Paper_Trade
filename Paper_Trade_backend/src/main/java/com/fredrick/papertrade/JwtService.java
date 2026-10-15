package com.fredrick.papertrade;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey key;
    private final long expiryMillis;

    public JwtService(@Value("${jwt.secret}") String secret, @Value("${jwt.expiry-hours}") long hours) {
        // The environment-backed secret signs and verifies every session token.
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiryMillis = hours * 60 * 60 * 1000;
    }

    // Creates the bearer token returned after successful signup or login.
    public String createToken(Long userId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiryMillis))
                .signWith(key)
                .compact();
    }

    /** Returns the user id inside a valid, unexpired token; throws if invalid or expired. */
    public Long readUserId(String token) {
        String subject = Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload().getSubject();
        return Long.valueOf(subject);
    }

    /** Reads "Bearer <token>" from a request header; throws 401 if missing, invalid or expired. */
    public Long userIdFromHeader(String header) {
        try {
            return readUserId(header.replaceFirst("^Bearer ", ""));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session expired, please log in again");
        }
    }
}
