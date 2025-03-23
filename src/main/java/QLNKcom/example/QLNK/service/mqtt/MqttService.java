package QLNKcom.example.QLNK.service.mqtt;

import QLNKcom.example.QLNK.model.User;
import QLNKcom.example.QLNK.model.adafruit.Feed;
import QLNKcom.example.QLNK.provider.user.UserProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class MqttService {

    private final MqttSubscriptionManager mqttSubscriptionManager;
    private final UserProvider userProvider;

    public Mono<Void> subscribeUserFeedsOnLogin(User user) {
        List<String> topics = user.getGroups().stream()
                .flatMap(group -> group.getFeeds().stream())
                .map(feed -> user.getUsername() + "/feeds/" + feed.getKey() + "/json")
                .toList();

        return mqttSubscriptionManager.subscribeFeed(user, topics)
                .onErrorResume(e -> {
                    log.error("Failed to subscribe feeds for user {}: {}", user.getUsername(), e.getMessage());
                    return Mono.empty();
                });
    }

    public Mono<Feed> updateUserFeedSubscription(User user, Feed feed) {
        String topic = user.getUsername() + "/feeds/" + feed.getKey() + "/json";
        return mqttSubscriptionManager.updateSubscription(user, topic).thenReturn(feed);
    }

    public Mono<Void> unsubscribeUserFeeds(User user) {
        return mqttSubscriptionManager.unsubscribeFeeds(user);
    }

    public Mono<Void> unsubscribeUserFeed(User user, Feed feed) {
        String topic = user.getUsername() + "/feeds/" + feed.getKey() + "/json";
        return mqttSubscriptionManager.unsubscribeFeed(user, topic);
    }

    private Message<String> createMqttMessage(String topic, String value) {
        return MessageBuilder.withPayload(value)
                .setHeader(MqttHeaders.TOPIC, topic)
                .build();
    }

    public Mono<Void> sendMqttCommand(String userId, String feed, String value) {
        return userProvider.findById(userId)
                .flatMap(user -> {
                    String topic = user.getUsername() + "/feeds/" + feed;
                    return mqttSubscriptionManager.getMqttPahoMessageHandler(userId)
                            .flatMap(handler -> {
                                Message<String> message = createMqttMessage(topic, value);
                                handler.handleMessage(message); // Gửi lên MQTT
                                log.info("🚀 Sent to MQTT: {} -> {}", topic, value);
                                return Mono.empty();
                            });
                });
    }

    public Mono<Void> unsubscribeGroupFeeds(User user, List<Feed> feeds) {
        return Flux.fromIterable(feeds)
                .flatMap(feed -> unsubscribeUserFeed(user, feed))
                .then();
    }


}
