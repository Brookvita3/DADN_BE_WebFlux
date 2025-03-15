package QLNKcom.example.QLNK.service.mqtt;

import QLNKcom.example.QLNK.model.User;
import QLNKcom.example.QLNK.service.websocket.WebSocketSessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
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
            String payload = (String) message.getPayload();
            log.info("🔔 MQTT Received for user {}: {}", user.getUsername(), payload);
            webSocketSessionManager.sendToUser(user.getUsername(), payload);
        });
    }
}
