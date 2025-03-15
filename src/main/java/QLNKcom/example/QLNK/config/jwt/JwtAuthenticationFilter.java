package QLNKcom.example.QLNK.config.jwt;

import QLNKcom.example.QLNK.exception.CustomAuthException;
import QLNKcom.example.QLNK.response.ResponseObject;
import QLNKcom.example.QLNK.service.user.CustomReactiveUserDetailsService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

@Component
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter implements WebFilter {

    private final JwtUtils jwtUtils;
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final CustomReactiveUserDetailsService customReactiveUserDetailsService;


    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String accessToken = extractAccessToken(exchange.getRequest());
        if (accessToken == null) {
            return chain.filter(exchange);
        }

        return jwtUtils.extractAccessEmail(accessToken)
                .flatMap(email -> validateTokenAndSetSecurityContext(exchange, chain, email, accessToken))
                .onErrorResume(CustomAuthException.class, ex -> handleAuthException(exchange, ex))
                .then(chain.filter(exchange));
    }

    private Mono<Void> validateTokenAndSetSecurityContext(ServerWebExchange exchange, WebFilterChain chain, String email, String accessToken) {
        return getRefreshTokenIat(email)
                .flatMap(refreshTokenIat -> jwtUtils.validateAccessToken(accessToken, refreshTokenIat / 1000))
                .flatMap(isValid -> isValid ? setSecurityContext(exchange, chain, email) : Mono.empty());
    }

    private Mono<Long> getRefreshTokenIat(String email) {
        return redisTemplate.opsForValue()
                .get("refresh:iat:" + email)
                .map(Long::parseLong)
                .switchIfEmpty(Mono.error(new CustomAuthException("Access token is revoked", HttpStatus.UNAUTHORIZED)));
    }

    private Mono<Void> setSecurityContext(ServerWebExchange exchange, WebFilterChain chain, String email) {
        return customReactiveUserDetailsService.findByUsername(email)
                .flatMap(user -> {
                    Authentication authentication = new UsernamePasswordAuthenticationToken(user, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
                    SecurityContext securityContext = new SecurityContextImpl(authentication);
                    return chain.filter(exchange)
                            .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(securityContext)));
                });
    }

    private String extractAccessToken(ServerHttpRequest request) {
        return Optional.ofNullable(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                .filter(authHeader -> authHeader.startsWith("Bearer "))
                .map(authHeader -> authHeader.substring(7))
                .orElse(null);
    }

    private Mono<Void> handleAuthException(ServerWebExchange exchange, CustomAuthException ex) {
        exchange.getResponse().setStatusCode(ex.getHttpStatus());
        return exchange.getResponse()
                .writeWith(Mono.just(exchange.getResponse()
                        .bufferFactory()
                        .wrap(serializeResponseObject(ex))));
    }

    private byte[] serializeResponseObject(CustomAuthException ex) {
        ResponseObject response = ResponseObject.builder()
                .message(ex.getMessage())
                .status(ex.getHttpStatus().value())
                .data(null)
                .build();

        try {
            return new ObjectMapper().writeValueAsBytes(response);
        } catch (JsonProcessingException e) {
            return "{\"message\":\"Internal Server Error\",\"status\":500}".getBytes();
        }
    }
}
