package QLNKcom.example.QLNK.service.mqtt;

import QLNKcom.example.QLNK.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class MqttSubscriptionManager {
    private final MqttClientFactory mqttClientFactory;
    private final MqttAdapterFactory mqttAdapterFactory;
    private final MqttMessageHandler mqttMessageHandler;
    private final Map<String, MqttPahoMessageDrivenChannelAdapter> activeSubscribers = new ConcurrentHashMap<>();
    private final Map<String, MqttPahoMessageHandler> activeHandlers = new ConcurrentHashMap<>();

    public Mono<Void> subscribeFeed(User user, List<String> feeds) {
        if (feeds.isEmpty()) {
            log.warn("User {} has no feeds to subscribe", user.getUsername());
            return Mono.empty();
        }

        String clientId = "mqtt-" + user.getId();
        return createNewAdapter(user, feeds, clientId).then()
                .onErrorResume(e -> {
                    log.error("❌ Failed to subscribe user {} on login: {}", user.getId(), e.getMessage());
                    return Mono.empty();
                });
    }

    public Mono<Void> updateSubscription(User user, String feed) {
        String clientId = "mqtt-" + user.getId();
        MqttPahoMessageDrivenChannelAdapter adapter = activeSubscribers.get(clientId);
        if (adapter == null) {
            log.warn("No active subscription found for user {}, subscribing instead", user.getUsername());
            return subscribeFeed(user, Collections.singletonList(feed));
        }

        return updateAdapterTopics(adapter, feed, user)
                .doOnSuccess(v -> log.info("✅ Updated subscription for user {} to feeds: {}", user.getUsername(), feed))
                .onErrorResume(e -> {
                    log.error("❌ Failed to update subscription for user {}: {}", user.getId(), e.getMessage());
                    return Mono.empty();
                });
    }

    private Mono<MqttPahoMessageDrivenChannelAdapter> createNewAdapter(User user, List<String> feeds, String clientId) {
        return Mono.fromCallable(() -> {
            MqttPahoClientFactory client = mqttClientFactory.createMqttClient(user.getUsername(), user.getApikey());
            MqttPahoMessageDrivenChannelAdapter adapter = mqttAdapterFactory.createMqttAdapter(user, client, feeds);
            adapter.start();

            MqttPahoMessageHandler messageHandler = mqttMessageHandler.createMessageHandler(user, client);
            mqttMessageHandler.attachHandler(adapter, user);

            activeSubscribers.put(clientId, adapter);
            activeHandlers.put(clientId, messageHandler);
            return adapter;
        });
    }

    private Mono<Void> updateAdapterTopics(MqttPahoMessageDrivenChannelAdapter adapter, String topic, User user) {
        return Mono.fromRunnable(() -> {
            Set<String> currentTopics = new HashSet<>(Arrays.asList(adapter.getTopic()));
            log.info("current topic: {}", currentTopics);

            if (!currentTopics.contains(topic)) {
                adapter.addTopic(topic);
                log.info("➕ Added topic {} for user {}", topic, user.getUsername());
            }
        });
    }

    public Mono<Void> unsubscribeFeed(User user) {
        return Mono.fromRunnable(() -> {
            String clientId = "mqtt-" + user.getId();
            MqttPahoMessageDrivenChannelAdapter adapter = activeSubscribers.remove(clientId);
            activeHandlers.remove(clientId);
            if (adapter != null) {
                adapter.stop();
                log.info("❌ Unsubscribed user {} from MQTT", user.getUsername());
            }
        });
    }

    public Mono<MqttPahoMessageDrivenChannelAdapter> getMqttAdapter(String userId) {
        MqttPahoMessageDrivenChannelAdapter adapter = activeSubscribers.get("mqtt-" + userId);
        if (adapter == null) {
            log.warn("❌ No active MQTT adapter found for user {}", userId);
            return Mono.empty();
        }
        return Mono.just(adapter);
    }

    public Mono<MqttPahoMessageHandler> getMqttPahoMessageHandler(String userId) {
        MqttPahoMessageHandler messageHandler = activeHandlers.get("mqtt-" + userId);
        if (messageHandler == null) {
            log.warn("❌ No active MQTT client found for user {}", userId);
            return Mono.empty();
        }
        return Mono.just(messageHandler);
    }

}
