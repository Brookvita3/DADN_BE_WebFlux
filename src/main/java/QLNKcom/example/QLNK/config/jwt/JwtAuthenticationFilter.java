package QLNKcom.example.QLNK.config.jwt;

import QLNKcom.example.QLNK.controller.auth.AuthController;
import QLNKcom.example.QLNK.exception.CustomAuthException;
import QLNKcom.example.QLNK.response.ResponseObject;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

@Component
@AllArgsConstructor
public class JwtAuthenticationFilter implements WebFilter {

    private final JwtUtils jwtUtils;
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final ServerSecurityContextRepository securityContextRepository;
    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);


    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String accessToken = extractAccessTokenFromRequest(request);

        if (accessToken == null) {
            return chain.filter(exchange);
        }

        return jwtUtils.extractAccessEmail(accessToken)
                .flatMap(email -> getRefreshTokenIat(email)
                        .flatMap(refreshTokenIat -> jwtUtils.validateAccessToken(accessToken, refreshTokenIat)
                                .flatMap(isValid -> isValid ? setSecurityContext(exchange, email) : Mono.empty())
                        )
                )
                .onErrorResume(CustomAuthException.class, ex -> {
                    log.error("JWT validation failed: {}", ex.getMessage());
                    exchange.getResponse().setStatusCode(ex.getHttpStatus());
                    return exchange.getResponse()
                            .writeWith(Mono.just(exchange.getResponse()
                                    .bufferFactory()
                                    .wrap(serializeResponseObject(ex))));
                })
                .switchIfEmpty(chain.filter(exchange));
    }

    private Mono<Long> getRefreshTokenIat(String email) {
        return redisTemplate.opsForValue()
                .get("refresh:iat:" + email)
                .map(Long::parseLong)
                .switchIfEmpty(Mono.error(  new CustomAuthException("Access token is revoke", HttpStatus.UNAUTHORIZED)));
    }

    private Mono<Void> setSecurityContext(ServerWebExchange exchange, String email) {
        SecurityContext context = new SecurityContextImpl(
                new UsernamePasswordAuthenticationToken(email, null, List.of())
        );
        return securityContextRepository.save(exchange, context).then();
    }

    private String extractAccessTokenFromRequest(ServerHttpRequest request) {
        return Optional.ofNullable(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                .filter(authHeader -> authHeader.startsWith("Bearer "))
                .map(authHeader -> authHeader.substring(7))
                .orElse(null);
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
