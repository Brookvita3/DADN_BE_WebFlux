package QLNKcom.example.QLNK.service.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class WebSocketSessionManager {
    private final Map<String, Sinks.Many<String>> userSinks = new ConcurrentHashMap<>();

    public void registerSession(String userId) {
        userSinks.putIfAbsent(userId, Sinks.many().multicast().onBackpressureBuffer());
    }

    public void removeSession(String userId) {
        userSinks.remove(userId);
    }

    public void sendToUser(String userId, String message) {
        if (userSinks.containsKey(userId)) {
            log.info("📤 Sending WebSocket message to user {}: {}", userId, message);
            userSinks.get(userId).tryEmitNext(message);
        }
    }

    public Flux<String> getUserFlux(String userId) {
        return userSinks.computeIfAbsent(userId, key -> Sinks.many().multicast().onBackpressureBuffer()).asFlux();
    }
}
