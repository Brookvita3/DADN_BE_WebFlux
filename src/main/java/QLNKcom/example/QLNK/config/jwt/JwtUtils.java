package QLNKcom.example.QLNK.config.jwt;

import QLNKcom.example.QLNK.exception.CustomAuthException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;

@Component
@Slf4j
public class JwtUtils {
    @Value("${jwt.secret.key}")
    private String accessSecretKey;

    @Value("${jwt.refresh.secret.key}")
    private String refreshSecretKey;

    @Value("${jwt.expiration}")
    private long accessExpirationTime;

    @Value("${jwt.refresh.expiration}")
    private long refreshExpirationTime;

    private SecretKey accessKey;
    private SecretKey refreshKey;

    @PostConstruct
    public void getSignInKey() {
        byte[] bytes_access = Base64.getDecoder().decode(accessSecretKey);
        this.accessKey = Keys.hmacShaKeyFor(bytes_access);
        byte[] bytes_refresh = Base64.getDecoder().decode(refreshSecretKey);
        this.refreshKey = Keys.hmacShaKeyFor(bytes_refresh);
    }

    public String generateAccessToken(String email) {
        return Jwts.builder()
                .subject(email)
                .claim("role", "USER")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessExpirationTime))
                .signWith(accessKey)
                .compact();
    }

    public String generateRefreshToken(String email) {
        return Jwts.builder()
                .subject(email)
                .claim("role", "USER")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshExpirationTime))
                .signWith(refreshKey)
                .compact();
    }

    public Claims extractAccessClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(accessKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            log.error("Payload access is not valid: {}", e.getMessage());
            throw new CustomAuthException("Payload access is not valid", HttpStatus.UNAUTHORIZED);
        }
    }

    public String extractAccessEmail(String token) {
        return extractAccessClaims(token).getSubject();
    }

    public Date extractAccessIat(String token) {
        return extractAccessClaims(token).getIssuedAt();
    }

    public boolean validateAccessToken(String accessToken, Long refreshTokenIatSeconds) {
        Date accessIat = extractAccessIat(accessToken);
        long accessIatSeconds = accessIat.getTime() / 1000;
        return accessIatSeconds >= refreshTokenIatSeconds;
    }

    public Claims extractRefreshClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(refreshKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            log.error("Payload refresh is not valid: {}", e.getMessage());
            throw new CustomAuthException("Payload refresh is not valid", HttpStatus.UNAUTHORIZED);
        }
    }

    public String extractRefreshEmail(String token) {
        return extractRefreshClaims(token).getSubject();
    }

    public Date extractRefreshIat(String token) {
        return extractRefreshClaims(token).getIssuedAt();
    }

    public boolean validateRefreshToken(String refreshToken, Long refreshIatRedis) {
        long refreshTokenIatSeconds = extractRefreshIat(refreshToken).getTime() / 1000;
        return refreshTokenIatSeconds == refreshIatRedis;
    }

    public String extractToken(ServerHttpRequest request) {
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new CustomAuthException("Missing or invalid Authorization header", HttpStatus.BAD_REQUEST);
        }
        return authHeader.substring(7);
    }

}
