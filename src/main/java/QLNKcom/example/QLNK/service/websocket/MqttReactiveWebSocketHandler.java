package QLNKcom.example.QLNK.service.websocket;

import QLNKcom.example.QLNK.config.jwt.JwtUtils;
import QLNKcom.example.QLNK.model.User;
import QLNKcom.example.QLNK.service.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
public class MqttReactiveWebSocketHandler implements WebSocketHandler {

    private final WebSocketSessionManager sessionManager;
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final JwtUtils jwtUtils;
    private final UserService userService;

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        return extractToken(session)
                .flatMap(token -> extractEmailFromToken(token)
                        .flatMap(email -> validateTokenWithRedis(token, email)))
                .flatMap(userService::findByEmail)
                .map(User::getId)
                .flatMap(userId -> establishWebSocketSession(userId, session))
                .switchIfEmpty(session.close());
    }

    private Mono<String> extractToken(WebSocketSession session) {
        return Mono.justOrEmpty(session.getHandshakeInfo().getHeaders().getFirst("Authorization"))
                .filter(authHeader -> authHeader.startsWith("Bearer "))
                .map(authHeader -> authHeader.replace("Bearer ", ""))
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("❌ No valid Authorization header, closing WebSocket");
                    return Mono.empty();
                }));
    }

    private Mono<String> extractEmailFromToken(String token) {
        return Mono.fromCallable(() -> jwtUtils.extractAccessEmail(token))
                .onErrorResume(e -> {
                    log.warn("❌ Invalid token: {}", e.getMessage());
                    return Mono.empty();
                });
    }
    private Mono<String> validateTokenWithRedis(String token, String email) {
        String redisKey = "refresh:iat:" + email;
        return redisTemplate.opsForValue().get(redisKey)
                .flatMap(refreshTokenIatStr -> {
                    if (refreshTokenIatStr == null) {
                        log.warn("❌ No refreshTokenIat found in Redis, closing WebSocket");
                        return Mono.empty();
                    }

                    long refreshTokenIat = Long.parseLong(refreshTokenIatStr);
                    return jwtUtils.validateAccessToken(token, refreshTokenIat)
                            ? Mono.just(email)
                            : Mono.defer(() -> {
                        log.warn("❌ Token revoked or expired, closing WebSocket");
                        return Mono.empty();
                    });
                });
    }

    private Mono<Void> establishWebSocketSession(String userId, WebSocketSession session) {
        sessionManager.registerSession(userId);
        log.info("✅ WebSocket connected: user {}", userId);

        return Mono.firstWithSignal(
                        session.receive()
                                .doOnNext(message -> log.info("📩 Received from {}: {}", userId, message.getPayloadAsText()))
                                .doOnError(error -> log.warn("❌ WebSocket error: {}", error.getMessage())) // Bắt lỗi nếu có
                                .then(),
                        session.send(sessionManager.getUserFlux(userId)
                                .map(session::textMessage))
                )
                .doFinally(signalType -> {
                    log.info("🔴 WebSocket disconnected: user {}", userId);
                    sessionManager.removeSession(userId);
                })
                .then();
    }


}
