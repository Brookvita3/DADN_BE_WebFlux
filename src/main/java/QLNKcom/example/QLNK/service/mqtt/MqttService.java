package QLNKcom.example.QLNK.service.mqtt;

import QLNKcom.example.QLNK.model.User;
import QLNKcom.example.QLNK.service.adafruit.AdafruitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class MqttService {

    private final MqttSubscriptionManager mqttSubscriptionManager;
    private final AdafruitService adafruitService;

    public Mono<Void> subscribeUserFeeds(User user) {
        return adafruitService.getUserFeeds(user.getUsername(), user.getApikey())
                .flatMap(feeds -> {
                    List<String> topics = feeds.stream()
                            .map(feed -> user.getUsername() + "/feeds/" + feed.getName() + "/json")
                            .toList();
                    return mqttSubscriptionManager.subscribeFeed(user, topics);
                });
    }

    public Mono<Void> unsubscribeUserFeeds(User user) {
        return mqttSubscriptionManager.unsubscribeFeed(user);
    }
}
