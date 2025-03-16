package QLNKcom.example.QLNK.service.mqtt;

import QLNKcom.example.QLNK.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
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
        return Mono.fromRunnable(() -> {

            if (feeds.isEmpty()) {
                log.warn("User {} has no feeds to subscribe", user.getUsername());
                return;
            }

            String clientId = "mqtt-" + user.getId();
            if (activeSubscribers.containsKey(clientId)) {
                log.info("🔄 User {} is already subscribed, skipping re-subscription", user.getUsername());
                return;
            }

            try {
                MqttPahoClientFactory client = mqttClientFactory.createMqttClient(user.getUsername(), user.getApikey());

                MqttPahoMessageDrivenChannelAdapter adapter = mqttAdapterFactory.createMqttAdapter(user, client, feeds);
                adapter.start();

                MqttPahoMessageHandler messageHandler = mqttMessageHandler.createMessageHandler(user, client);
                mqttMessageHandler.attachHandler(adapter, user);

                activeSubscribers.put(clientId, adapter);
                activeHandlers.put(clientId, messageHandler);

                log.info("✅ Subscribed user {} to feeds: {}", user.getUsername(), feeds);
            }
            catch (Exception e) {
                log.error("❌ Failed to subscribe user {}: {}", user.getId(), e.getMessage(), e);
            }

        });
    }

    public Mono<Void> unsubscribeFeed(User user) {
        return Mono.fromRunnable(() -> {

            String clientId = "mqtt-" + user.getId();
            if (activeSubscribers.containsKey(clientId)) {
                MqttPahoMessageDrivenChannelAdapter adapter = activeSubscribers.remove(clientId);
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

    public Mono<MqttPahoMessageHandler> getMqttMessageHandler(String userId) {
        MqttPahoMessageHandler messageHandler = activeHandlers.get("mqtt-" + userId);
        if (messageHandler == null) {
            log.warn("❌ No active MQTT client found for user {}", userId);
            return Mono.empty();
        }
        return Mono.just(messageHandler);
    }

}
