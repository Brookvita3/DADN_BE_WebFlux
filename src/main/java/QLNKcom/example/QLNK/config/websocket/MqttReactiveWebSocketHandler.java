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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class MqttReactiveWebSocketHandler implements WebSocketHandler {

    private final WebSocketSessionManager sessionManager;
    private final RedisService redisService;
    private final JwtUtils jwtUtils;
    private final MqttService mqttService;
    private final UserProvider userProvider;
    private final ObjectMapper objectMapper;


    @Override
    public Mono<Void> handle(WebSocketSession session) {
        log.info("Checking for authentication in session attributes");
        return Mono.justOrEmpty(session.getAttributes().get("websocket.auth"))
                .switchIfEmpty(Mono.error(new CustomAuthException("No principal found", HttpStatus.UNAUTHORIZED)))
                .cast(UsernamePasswordAuthenticationToken.class)
                .flatMap(auth -> {
                    String email = auth.getName();
                    log.info("Principal found: email={}", email);
                    return userProvider.findByEmail(email)
                            .switchIfEmpty(Mono.error(new CustomAuthException("User not found", HttpStatus.UNAUTHORIZED)))
                            .map(User::getId)
                            .flatMap(userId -> establishWebSocketSession(userId, session));
                })
                .doOnError(error -> log.error("WebSocket authentication failed: {}", error.getMessage(), error));
    }

    public Mono<UsernamePasswordAuthenticationToken> handleHandshake(ServerWebExchange exchange) {
        return extractToken(exchange)
                .switchIfEmpty(Mono.error(new CustomAuthException("No token provided", HttpStatus.UNAUTHORIZED)))
                .flatMap(token -> extractEmailFromToken(token)
                        .flatMap(email -> validateTokenWithRedis(token, email)))
                .flatMap(email -> userProvider.findByEmail(email)
                        .switchIfEmpty(Mono.error(new CustomAuthException("User not found", HttpStatus.UNAUTHORIZED)))
                        .map(user -> new UsernamePasswordAuthenticationToken(
                                user.getEmail(),
                                null,
                                List.of(() -> "ROLE_USER")
                        )))
                .doOnSuccess(auth -> log.info("Handshake successful for email: {}", auth.getName()))
                .doOnError(error -> log.error("Handshake failed: {}", error.getMessage(), error));
    }

    private Mono<String> extractToken(ServerWebExchange exchange) {
        String query = exchange.getRequest().getURI().getQuery();
        if (query != null && query.contains("token=")) {
            String[] params = query.split("&");
            for (String param : params) {
                if (param.startsWith("token=")) {
                    log.info("token: {}", param.substring(6));
                    return Mono.just(param.substring(6));  // Trả về token
                }
            }
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

        String query = session.getHandshakeInfo().getUri().getQuery();
        String queryFeed = null;
        if (query != null) {
            String[] params = query.split("&");
            for (String param : params) {
                if (param.startsWith("key=")) {
                    queryFeed = param.substring(4);
                    break;
                }
            }
        }
        final String feed = queryFeed;
        log.info("Feed key: {}", feed);

        // Handle incoming messages as a Flux
        Flux<Void> receiveFlux = session.receive()
                .doOnNext(message -> log.info("📨 Received message from user {}: {}", userId, message.getPayloadAsText()))
                .flatMap(message -> handleWebSocketMessage(userId, feed, message.getPayloadAsText())
                        .onErrorResume(e -> {
                            log.error("❌ Error handling message for user {}: {}", userId, e.getMessage(), e);
                            return Mono.empty(); // Continue processing next messages
                        }))
                .doOnError(error -> log.error("❌ WebSocket receive error for user {}: {}", userId, error.getMessage(), error));

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
            JsonNode jsonNode = objectMapper.readTree(json);
            String key = jsonNode.get("key").asText();
            return requestedKey == null || key.equals(requestedKey);
        } catch (Exception e) {
            log.error("❌ Error parsing JSON: {}", json, e);
            return false;
        }
    }

}
