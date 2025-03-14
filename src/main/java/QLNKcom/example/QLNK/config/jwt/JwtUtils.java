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

    public Mono<String> generateAccessToken(String email) {
        return Mono.fromCallable(() ->
                Jwts.builder()
                        .subject(email)
                        .claim("role", "USER")
                        .issuedAt(new Date())
                        .expiration(new Date(System.currentTimeMillis() + accessExpirationTime))
                        .signWith(accessKey)
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
                        .signWith(refreshKey)
                        .compact()
        );
    }

    public Mono<Claims> extractAccessClaims(String token) {
        return Mono.fromCallable(() ->
                Jwts.parser()
                        .verifyWith(accessKey)
                        .build()
                        .parseSignedClaims(token)
                        .getPayload()
        ).onErrorMap(e -> {
            log.error("Payload access is not valid: {}", e.getMessage());
            return new CustomAuthException("Payload access is not valid", HttpStatus.UNAUTHORIZED);
        });
    }

    public Mono<String> extractAccessEmail(String token) {
        return extractAccessClaims(token).map(Claims::getSubject);
    }

    public Mono<Date> extractAccessIat(String token) {
        return extractAccessClaims(token).map(Claims::getIssuedAt);
    }

    public Mono<Boolean> validateAccessToken(String accessToken, Long refreshTokenIatSeconds) {
        return extractAccessIat(accessToken)
                .flatMap(accessIat -> {
                    long accessIatSeconds = accessIat.getTime() / 1000;
                    return Mono.just(accessIatSeconds >= refreshTokenIatSeconds);
                })
                .defaultIfEmpty(false);
    }

    public Mono<Claims> extractRefreshClaims(String token) {
        return Mono.fromCallable(() ->
                Jwts.parser()
                        .verifyWith(refreshKey)
                        .build()
                        .parseSignedClaims(token)
                        .getPayload()
        ).onErrorMap(e -> {
            log.error("Payload refresh is not valid: {}", e.getMessage());
            return new CustomAuthException("Payload refresh is not valid", HttpStatus.UNAUTHORIZED);
        });
    }

    public Mono<String> extractRefreshEmail(String token) {
        return extractRefreshClaims(token).map(Claims::getSubject);
    }

    public Mono<Date> extractRefreshIat(String token) {
        return extractRefreshClaims(token).map(Claims::getIssuedAt);
    }

    public Mono<Boolean> validateRefreshToken(String refreshToken, Long refreshIatRedis) {
        return extractRefreshIat(refreshToken)
                .flatMap(refreshIat -> {
                    long refreshTokenIatSeconds = refreshIat.getTime() / 1000;
                    return Mono.just(refreshTokenIatSeconds == refreshIatRedis);
                })
                .defaultIfEmpty(false);
    }

    public Mono<String> extractToken(ServerHttpRequest request) {
        return Mono.justOrEmpty(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                .filter(authHeader -> authHeader.startsWith("Bearer "))
                .map(authHeader -> authHeader.substring(7))
                .switchIfEmpty(Mono.error(new CustomAuthException("Missing or invalid Authorization header", HttpStatus.BAD_REQUEST)));
    }

}
