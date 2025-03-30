package QLNKcom.example.QLNK.service.mqtt;

import QLNKcom.example.QLNK.model.User;
import QLNKcom.example.QLNK.model.adafruit.Feed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class MqttService {

    private final MqttSubscriptionManager mqttSubscriptionManager;
    private final MqttCommandService mqttCommandService;

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

    public Mono<Void> sendMqttCommand(String userId, String feed, String value) {
        return mqttCommandService.sendMqttCommand(userId, feed, value);
    }

    public Mono<Void> unsubscribeGroupFeeds(User user, List<Feed> feeds) {
        return Flux.fromIterable(feeds)
                .flatMap(feed -> unsubscribeUserFeed(user, feed))
                .then();
    }

    public Mono<Void> updateFeedSubscription(User user, String oldFullFeedKey, String newFullFeedKey) {
        String oldTopic = user.getUsername() + "/feeds/" + oldFullFeedKey + "/json";
        String newTopic = user.getUsername() + "/feeds/" + newFullFeedKey + "/json";
        return mqttSubscriptionManager.unsubscribeFeed(user, oldTopic)
                .then(mqttSubscriptionManager.updateSubscription(user, newTopic));
    }
}
