package com.studyhub.security;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.time.Instant;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.io.Decoders;

@Service
public class JwtService {
    private final SecretKey secret;
    private final long expiration;

    public JwtService(@Value("${jwt.secret}") String secret, @Value("${jwt.expiration}") long expiration) {
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        this.secret = Keys.hmacShaKeyFor(keyBytes);
        this.expiration = expiration;
    }
    public String generateToken(UserDetails userDetails) {
        Instant now = Instant.now();
        return Jwts.builder()
                    .subject(userDetails.getUsername())
                    .issuedAt(Date.from(now))
                    .expiration(Date.from(now.plusMillis(expiration)))
                    .signWith(secret, Jwts.SIG.HS256)
                    .compact();
    }

    public String extractUsername(String token) {
        return extractClaim(
                token,
                Claims::getSubject
        );
    }

    public <T> T extractClaim(
            String token,
            Function<Claims, T> claimsResolver
    ) {
        Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public boolean isTokenValid(
            String token,
            UserDetails userDetails
    ) {
        try {
            String username = extractUsername(token);
            return username.equals(userDetails.getUsername());
        } 
        catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(secret)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}