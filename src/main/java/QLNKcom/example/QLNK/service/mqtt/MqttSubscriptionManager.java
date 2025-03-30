package QLNKcom.example.QLNK.service.mqtt;

import QLNKcom.example.QLNK.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

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

    /**
     * @param  topic  the topic must have format username/feeds/feedKey/json
     */
    public Mono<Void> updateSubscription(User user, String topic) {
        String clientId = "mqtt-" + user.getId();
        MqttPahoMessageDrivenChannelAdapter adapter = activeSubscribers.get(clientId);
        if (adapter == null) {
            log.warn("No active subscription found for user {}, subscribing instead", user.getUsername());
            return subscribeFeed(user, Collections.singletonList(topic));
        }

        return updateAdapterTopics(adapter, topic, user)
                .doOnSuccess(v -> log.info("✅ Updated subscription for user {} to feeds: {}", user.getUsername(), topic))
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
            mqttMessageHandler.attachHandler(adapter, user);
            activeSubscribers.put(clientId, adapter);
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

    public Mono<Void> unsubscribeFeeds(User user) {
        return Mono.fromRunnable(() -> {
            String clientId = "mqtt-" + user.getId();
            MqttPahoMessageDrivenChannelAdapter adapter = activeSubscribers.remove(clientId);
            if (adapter != null) {
                adapter.stop();
                log.info("❌ Unsubscribed user {} from MQTT", user.getUsername());
            }
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    /**
     * @param topic the topic must have format username/feeds/feedKey/json
     */
    public Mono<Void> unsubscribeFeed(User user, String topic) {
        String clientId = "mqtt-" + user.getId();
        MqttPahoMessageDrivenChannelAdapter adapter = activeSubscribers.get(clientId);

        return Mono.fromRunnable(() -> {
            if (adapter != null) {
                Set<String> currentTopics = new HashSet<>(Arrays.asList(adapter.getTopic()));
                if (currentTopics.contains(topic)) {
                    adapter.removeTopic(topic);
                    currentTopics.remove(topic);
                    log.info("➖ Removed topic {} for user {}", topic, user.getUsername());

                    if (currentTopics.isEmpty()) {
                        adapter.stop();
                        activeSubscribers.remove(clientId);
                        log.info("🗑️ Removed adapter for user {} as no topics remain", user.getUsername());
                    }
                } else {
                    log.info("❌ Topic {} is not subscribed by adapter of user {}", topic, user.getUsername());
                }
            }
        });
    }

}
