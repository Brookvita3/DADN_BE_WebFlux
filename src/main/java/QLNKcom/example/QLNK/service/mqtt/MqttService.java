package QLNKcom.example.QLNK.service.mqtt;

import QLNKcom.example.QLNK.model.User;
import QLNKcom.example.QLNK.service.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class MqttService {

    private final MqttSubscriptionManager mqttSubscriptionManager;
    private final UserService userService;

    public Mono<Void> subscribeUserFeeds(User user) {
        List<String> topics = user.getGroups().stream()
                .flatMap(group -> group.getFeeds().stream())
                .map(feed -> user.getUsername() + "/feeds/" + feed.getKey() + "/json")
                .toList();

        return mqttSubscriptionManager.subscribeFeed(user, topics);
    }

    public Mono<Void> unsubscribeUserFeeds(User user) {
        return mqttSubscriptionManager.unsubscribeFeed(user);
    }

    private Message<String> createMqttMessage(String topic, String value) {
        return MessageBuilder.withPayload(value)
                .setHeader(MqttHeaders.TOPIC, topic)
                .build();
    }

    public Mono<Void> sendMqttCommand(String userId, String feed, String value) {
        return userService.findById(userId)
                .flatMap(user -> {
                    String topic = user.getUsername() + "/feeds/" + feed;
                    return mqttSubscriptionManager.getMqttMessageHandler(userId)
                            .flatMap(handler -> {
                                Message<String> message = createMqttMessage(topic, value);
                                handler.handleMessage(message); // Gửi lên MQTT
                                log.info("🚀 Sent to MQTT: {} -> {}", topic, value);
                                return Mono.empty();
                            });
                });
    }


}
