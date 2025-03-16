package QLNKcom.example.QLNK.service.mqtt;

import QLNKcom.example.QLNK.model.User;
import QLNKcom.example.QLNK.service.websocket.WebSocketSessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class MqttMessageHandler {
    private final WebSocketSessionManager webSocketSessionManager;

    public void attachHandler(MqttPahoMessageDrivenChannelAdapter adapter, User user) {
        DirectChannel mqttChannel = new DirectChannel();
        adapter.setOutputChannel(mqttChannel);

        mqttChannel.subscribe(message -> {
            String topic = (String) message.getHeaders().get(MqttHeaders.RECEIVED_TOPIC);
            String payload = (String) message.getPayload();

            if (payload.isBlank()) {
                log.warn("⚠️ Received empty payload from topic {}", topic);
                return;
            }

            log.info("🔔 MQTT Received from topic {}: {}", topic, payload);

            webSocketSessionManager.sendToUser(user.getId(), payload);
        });
    }
}
