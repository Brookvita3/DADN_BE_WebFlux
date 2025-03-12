package QLNKcom.example.QLNK.config.jwt;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter implements WebFilter {

    private final JwtUtils jwtUtils;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String token = extractToken(exchange.getRequest());
        if (token == null) {
            return chain.filter(exchange);
        }
        return jwtUtils.validateToken(token)
                .flatMap(valid -> {
                    if (!valid) {
                        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                        return exchange.getResponse().setComplete();
                    }
                    return jwtUtils.getEmailFromToken(token)
                            .flatMap(email -> {
                                Authentication auth = new UsernamePasswordAuthenticationToken(email, null, List.of());
                                SecurityContext context = SecurityContextImpl.withAuthentication(auth);
                                return chain.filter(exchange)
                                        .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(context)));
                            });
                });
    }

    private String extractToken(ServerHttpRequest request) {
        return request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
    }
}
