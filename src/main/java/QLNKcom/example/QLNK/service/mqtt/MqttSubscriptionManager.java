package QLNKcom.example.QLNK.service.mqtt;

import QLNKcom.example.QLNK.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter;
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
    private final MqttMessageHandler mqttMessageHandler;
    private final Map<String, MqttPahoMessageDrivenChannelAdapter> activeSubscribers = new ConcurrentHashMap<>();

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
                MqttPahoMessageDrivenChannelAdapter adapter = new MqttPahoMessageDrivenChannelAdapter(
                        clientId,
                        mqttClientFactory.createMqttClientFactory(user.getUsername(), user.getApikey()),
                        feeds.toArray(new String[0])
                );

                adapter.setQos(1);
                adapter.setConverter(new DefaultPahoMessageConverter());

                mqttMessageHandler.attachHandler(adapter, user);

                adapter.start();
                activeSubscribers.put(clientId, adapter);

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
}
