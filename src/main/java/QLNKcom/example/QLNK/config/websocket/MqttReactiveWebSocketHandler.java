package QLNKcom.example.QLNK.config.websocket;

import QLNKcom.example.QLNK.config.jwt.JwtUtils;
import QLNKcom.example.QLNK.exception.CustomAuthException;
import QLNKcom.example.QLNK.model.User;
import QLNKcom.example.QLNK.provider.user.UserProvider;
import QLNKcom.example.QLNK.service.mqtt.MqttService;
import QLNKcom.example.QLNK.service.redis.RedisService;
import QLNKcom.example.QLNK.service.websocket.WebSocketSessionManager;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
public class MqttReactiveWebSocketHandler implements WebSocketHandler {

    private final WebSocketSessionManager sessionManager;
    private final RedisService redisService;
    private final JwtUtils jwtUtils;
    private final MqttService mqttService;
    private final UserProvider userProvider;

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        return session.getHandshakeInfo()
                .getPrincipal()
                .flatMap(principal -> userProvider.findByEmail(principal.getName()).map(User::getId)
                        .flatMap(userid -> establishWebSocketSession(userid, session)));
    }

    public Mono<ServerWebExchange> handleHandshake(ServerWebExchange exchange) {
        return extractToken(exchange)
                .switchIfEmpty(Mono.error(new CustomAuthException("No token provided", HttpStatus.UNAUTHORIZED)))
                .flatMap(token -> extractEmailFromToken(token)
                        .flatMap(email -> validateTokenWithRedis(token, email)))
                .flatMap(userProvider::findByEmail)
                .map(User::getId)
                .doOnSuccess(userId -> exchange.getAttributes().put("userId", userId))
                .thenReturn(exchange)
                .doOnError(error -> {
                    throw new CustomAuthException("Authentication failed", HttpStatus.UNAUTHORIZED);
                });
    }

    private Mono<String> extractToken(ServerWebExchange exchange) {
        String token = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            return Mono.just(token.substring(7));
        }
        return Mono.empty();
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
        return redisService.getValue(redisKey)
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

        String type = session.getHandshakeInfo().getUri().getQuery();
        String feed = (type != null && type.startsWith("key=")) ? type.substring(4) : null;

        // Handle incoming messages as a Flux
        Flux<Void> receiveFlux = session.receive()
                .doOnNext(message -> log.info("📨 Received message from user {}: {}", userId, message.getPayloadAsText()))
                .flatMap(message -> handleWebSocketMessage(userId, feed, message.getPayloadAsText())
                        .onErrorResume(e -> {
                            log.error("❌ Error handling message for user {}: {}", userId, e.getMessage(), e);
                            return Mono.empty(); // Continue processing next messages
                        }))
                .doOnError(error -> log.error("❌ WebSocket receive error for user {}: {}", userId, error.getMessage(), error))
                .map(message -> null); // Convert to Flux<Void> without completing

        // Handle outgoing messages as a Flux
        Mono<Void> sendFlux = session.send(sessionManager.getUserFlux(userId)
                        .filter(json -> shouldSendData(json, feed))
                        .map(session::textMessage))
                .doOnError(error -> log.error("❌ Send error for user {}: {}", userId, error.getMessage(), error));

        // Merge receive and send pipelines, keep session alive until client disconnects or error
        return Flux.merge(receiveFlux, sendFlux)
                .then()
                .doFinally(signalType -> {
                    log.info("🔴 WebSocket disconnected: user {} (Reason: {})", userId, signalType);
                    sessionManager.removeSession(userId);
                });
    }

    private Mono<Void> handleWebSocketMessage(String userId, String feed, String message) {
        log.info("📩 Received WebSocket message from {}: {}", userId, message);

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(message);

            String value = jsonNode.get("value").asText();

            log.info("✅ Parsed JSON -> Feed: {}, Value: {}", feed, value);
            return mqttService.sendMqttCommand(userId, feed, value);
        } catch (Exception e) {
            log.error("❌ Invalid WebSocket message format", e);
            return Mono.empty();
        }
    }


    private boolean shouldSendData(String json, String requestedKey) {
        try {
            JsonNode jsonNode = new ObjectMapper().readTree(json);
            String key = jsonNode.get("key").asText();
            return requestedKey == null || key.equals(requestedKey);
        } catch (Exception e) {
            log.error("❌ Error parsing JSON: {}", json, e);
            return false;
        }
    }

}
