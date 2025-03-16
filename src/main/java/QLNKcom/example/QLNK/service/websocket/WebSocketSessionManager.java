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
        Sinks.Many<String> sink = userSinks.remove(userId);
        if (sink != null) {
            sink.tryEmitComplete();
        }
    }

    public void sendToUser(String userId, String message) {
        if (userSinks.containsKey(userId)) {
            Sinks.Many<String> sink = userSinks.get(userId);

            long subscribers = sink.currentSubscriberCount();
            if (subscribers == 0) {
                log.warn("⚠️ No active WebSocket subscribers for user {}", userId);
                return;
            }

            log.info("📤 Sending WebSocket message to user {}: {}", userId, message);
            Sinks.EmitResult result = sink.tryEmitNext(message);

            if (result.isFailure()) {
                log.error("❌ Failed to send WebSocket message to user {}: {}", userId, result);
            }
        } else {
            log.warn("❌ No sink found for user {}", userId);
        }
    }


    public Flux<String> getUserFlux(String userId) {
        return userSinks.computeIfAbsent(userId, key -> Sinks.many().multicast().onBackpressureBuffer()).asFlux();
    }
}
