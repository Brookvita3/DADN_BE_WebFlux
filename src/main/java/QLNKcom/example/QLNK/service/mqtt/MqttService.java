package QLNKcom.example.QLNK.service.mqtt;

import QLNKcom.example.QLNK.model.User;
import QLNKcom.example.QLNK.model.adafruit.Feed;
import QLNKcom.example.QLNK.service.adafruit.AdafruitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class MqttService {

    @Value("${mqtt.broker.url}")
    private String brokerUrl;

    private final MqttClientFactory mqttClientFactory;
    private final AdafruitService adafruitService;
    private final Map<String, MqttPahoMessageDrivenChannelAdapter> activeSubscribers = new ConcurrentHashMap<>();


    public Mono<Void> subscribeFeed(User user) {
        return unsubscribeFeed(user)
                .then(adafruitService.getUserFeeds(user.getUsername(), user.getApikey())) // Lấy feeds
                .flatMapMany(Flux::fromIterable)
                .map(Feed::getName)
                .collectList()
                .flatMap(feeds -> {
                    if (feeds.isEmpty()) {
                        log.warn("User has no feeds to subscribe");
                        return Mono.empty();
                    }

                    String clientId = "mqtt-client-" + user.getId();
                    MqttPahoMessageDrivenChannelAdapter adapter = new MqttPahoMessageDrivenChannelAdapter(
                            clientId,
                            mqttClientFactory.createMqttClientFactory(user.getUsername(), user.getApikey()),
                            feeds.toArray(new String[0])
                    );

                    adapter.setOutputChannel(new DirectChannel());
                    adapter.start();

                    activeSubscribers.put(clientId, adapter);
                    log.info("✅ Subscribed user {} to feeds: {}", user.getUsername(), feeds);
                    return Mono.empty();
                });
    }

    public Mono<Void> unsubscribeFeed(User user) {
        return Mono.fromRunnable(() -> {
            String clientId = "mqtt-client-" + user.getId();
            if (activeSubscribers.containsKey(clientId)) {
                MqttPahoMessageDrivenChannelAdapter adapter = activeSubscribers.get(clientId);
                adapter.stop(); // Dừng MQTT
                activeSubscribers.remove(clientId);
                log.info("❌ Unsubscribed user {} from MQTT", user.getUsername());
            }
        });
    }



    public MqttPahoClientFactory createMqttClientFactory(String username, String apiKey) {
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();

        MqttConnectOptions mqttConnectOptions = new MqttConnectOptions();
        mqttConnectOptions.setServerURIs(new String[]{brokerUrl});
        mqttConnectOptions.setUserName(username);
        mqttConnectOptions.setPassword(apiKey.toCharArray());

        factory.setConnectionOptions(mqttConnectOptions);
        return factory;
    }

}
