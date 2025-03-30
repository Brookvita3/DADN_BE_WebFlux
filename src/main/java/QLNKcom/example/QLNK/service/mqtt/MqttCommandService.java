package QLNKcom.example.QLNK.service.mqtt;

import QLNKcom.example.QLNK.model.User;
import QLNKcom.example.QLNK.provider.user.UserProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler;
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class MqttCommandService {
    private final MqttClientFactory mqttClientFactory;
    private final UserProvider userProvider;
    private final Map<String, MqttPahoMessageHandler> activeHandlers = new ConcurrentHashMap<>();

    private MqttPahoMessageHandler getOrCreateHandler(String userId, User user) {
        return activeHandlers.computeIfAbsent(userId, id -> {
            MqttPahoClientFactory client = mqttClientFactory.createMqttClient(user.getUsername(), user.getApikey());
            MqttPahoMessageHandler handler = new MqttPahoMessageHandler("mqtt-handler-" + userId, client);
            handler.setAsync(true);
            handler.setConverter(new DefaultPahoMessageConverter());
            return handler;
        });
    }


    public Mono<Void> sendMqttCommand(String userId, String feed, String value) {
        return userProvider.findById(userId)
                .flatMap(user -> {
                    String topic = user.getUsername() + "/feeds/" + feed;
                    Message<String> message = MessageBuilder.withPayload(value)
                            .setHeader(MqttHeaders.TOPIC, topic)
                            .build();
                    MqttPahoMessageHandler handler = getOrCreateHandler(userId, user);
                    handler.handleMessage(message);
                    log.info("🚀 Sent to MQTT: {} -> {}", topic, value);
                    return Mono.empty();
                })
                .onErrorResume(e -> {
                    log.error("❌ Failed to send MQTT command for user {}: {}", userId, e.getMessage());
                    return Mono.empty();
                }).then();
    }

}
