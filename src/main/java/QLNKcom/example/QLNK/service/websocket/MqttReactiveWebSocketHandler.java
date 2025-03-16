package QLNKcom.example.QLNK.service.websocket;

import QLNKcom.example.QLNK.config.jwt.JwtUtils;
import QLNKcom.example.QLNK.model.User;
import QLNKcom.example.QLNK.service.mqtt.MqttService;
import QLNKcom.example.QLNK.service.user.UserService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
public class MqttReactiveWebSocketHandler implements WebSocketHandler {

    private final WebSocketSessionManager sessionManager;
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final JwtUtils jwtUtils;
    private final MqttService mqttService;
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
                                .doOnError(error -> {
                                    log.error("❌ WebSocket error for user {}: {}", userId, error.getMessage(), error);
                                })
                                .flatMap(message -> handleWebSocketMessage(userId, message.getPayloadAsText()))
                                .then(),
                        session.send(sessionManager.getUserFlux(userId)
                                .map(session::textMessage))
                )
                .doFinally(signalType -> {
                    log.info("🔴 WebSocket disconnected: user {} (Reason: {})", userId, signalType);
                    sessionManager.removeSession(userId);
                })
                .then();
    }

    private Mono<Void> handleWebSocketMessage(String userId, String message) {
        log.info("📩 Received WebSocket message from {}: {}", userId, message);

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(message);

            String feed = jsonNode.get("feed").asText();
            String value = jsonNode.get("value").asText();

            log.info("✅ Parsed JSON -> Feed: {}, Value: {}", feed, value);
            return mqttService.sendMqttCommand(userId, feed, value);
        } catch (Exception e) {
            log.error("❌ Invalid WebSocket message format", e);
            return Mono.empty();
        }
    }



}
