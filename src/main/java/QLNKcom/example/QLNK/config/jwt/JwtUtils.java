package QLNKcom.example.QLNK.config.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;

@Component
@Slf4j
public class JwtUtils {
    @Value("${jwt.secret.key}")
    private String secretKey;

    @Value("${jwt.expiration}")
    private long accessExpirationTime;

    @Value("${jwt.refresh.expiration}")
    private long refreshExpirationTime;

    private SecretKey key;

    @PostConstruct
    public void getSignInKey() {
        byte[] bytes = Base64.getDecoder().decode(secretKey);
        this.key = Keys.hmacShaKeyFor(bytes);
    }

    public Mono<String> generateAccessToken(String email) {
        return Mono.fromCallable(() ->
                Jwts.builder()
                        .subject(email)
                        .claim("role", "USER")
                        .issuedAt(new Date())
                        .expiration(new Date(System.currentTimeMillis() + accessExpirationTime))
                        .signWith(key)
                        .compact()
        );
    }

    public Mono<String> generateRefreshToken(String email) {
        return Mono.fromCallable(() ->
                Jwts.builder()
                        .subject(email)
                        .claim("role", "USER")
                        .issuedAt(new Date())
                        .expiration(new Date(System.currentTimeMillis() + refreshExpirationTime))
                        .signWith(key)
                        .compact()
        );
    }

    public Mono<Claims> extractClaims(String token) {
        return Mono.fromCallable(() ->
                Jwts.parser()
                        .verifyWith(key)
                        .build()
                        .parseSignedClaims(token)
                        .getPayload()
        ).onErrorResume(e -> {
            log.error("Payload is not valid: {}", e.getMessage());
            return Mono.empty();
        });
    }

    public Mono<String> extractEmail(String token) {
        return extractClaims(token).map(Claims::getSubject);
    }

    public Mono<Date> extractIat(String token) {
        return extractClaims(token).map(Claims::getIssuedAt);
    }

    public Mono<Boolean> validateAccessToken(String accessToken, Long refreshTokenIatSeconds) {
        return extractIat(accessToken)
                .map(accessIat -> {
                    long accessIatSeconds = accessIat.getTime() / 1000;
                    return accessIatSeconds >= refreshTokenIatSeconds;
                })
                .defaultIfEmpty(false);
    }

}
