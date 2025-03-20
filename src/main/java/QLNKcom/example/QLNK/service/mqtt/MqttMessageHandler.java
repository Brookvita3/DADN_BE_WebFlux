package QLNKcom.example.QLNK.service.mqtt;

import QLNKcom.example.QLNK.enums.DeviceType;
import QLNKcom.example.QLNK.enums.SensorType;
import QLNKcom.example.QLNK.model.User;
import QLNKcom.example.QLNK.model.data.DeviceData;
import QLNKcom.example.QLNK.model.data.SensorData;
import QLNKcom.example.QLNK.repository.DeviceDataRepository;
import QLNKcom.example.QLNK.repository.SensorDataRepository;
import QLNKcom.example.QLNK.service.websocket.WebSocketSessionManager;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler;
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@Slf4j
@RequiredArgsConstructor
public class MqttMessageHandler {
    @Value("${mqtt.broker.url}")
    private String brokerUrl;

    private final WebSocketSessionManager webSocketSessionManager;
    private final DeviceDataRepository deviceDataRepository;
    private final SensorDataRepository sensorDataRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private JsonNode parsePayload(String payload) {
        try {
            return objectMapper.readTree(payload).get("data");
        } catch (JsonProcessingException e) {
            log.error("❌ Failed to parse JSON payload: {}", e.getMessage());
            return null;
        }
    }

    private void saveDeviceData(User user, String groupKey, String feedKey, String valueStr, String payload) {
        boolean status = "1".equals(valueStr) || "true".equalsIgnoreCase(valueStr);
        DeviceData deviceData = DeviceData.builder()
                .username(user.getUsername())
                .groupKey(groupKey)
                .feedKey(feedKey)
                .status(status)
                .timeStamp(Instant.now())
                .build();

        deviceDataRepository.save(deviceData)
                .doOnSuccess(savedData -> webSocketSessionManager.sendToUser(user.getId(), payload))
                .doOnError(error -> log.error("❌ Error saving Device Data: {}", error.getMessage()))
                .subscribe();
    }

    private void saveSensorData(User user, String groupKey, String feedKey, String valueStr, String payload) {
        try {
            double value = Double.parseDouble(valueStr);
            SensorData sensorData = SensorData.builder()
                    .username(user.getUsername())
                    .groupKey(groupKey)
                    .feedKey(feedKey)
                    .value(value)
                    .timeStamp(Instant.now())
                    .build();

            sensorDataRepository.save(sensorData)
                    .doOnSuccess(savedData -> webSocketSessionManager.sendToUser(user.getId(), payload))
                    .doOnError(error -> log.error("❌ Error saving Sensor Data: {}", error.getMessage()))
                    .subscribe();
        } catch (NumberFormatException e) {
            log.error("❌ Invalid sensor data: {}", payload);
        }
    }


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

            String[] parts = topic.split("/");
            if (parts.length < 4) {
                log.warn("⚠️ Invalid topic format: {}", topic);
                return;
            }

            String[] groupAndFeed = parts[2].split("\\.");
            if (groupAndFeed.length < 2) {
                log.warn("⚠️ Invalid feed format: {}", parts[2]);
                return;
            }

            String groupKey = groupAndFeed[0];
            String feedKey = groupAndFeed[1];
            JsonNode dataNode = parsePayload(payload);
            if (dataNode == null || dataNode.get("value") == null) {
                log.warn("⚠️ Missing 'data.value' in payload: {}", payload);
                return;
            }

            String valueStr = dataNode.get("value").asText();

            if (DeviceType.isDevice(feedKey)) {
                saveDeviceData(user, groupKey, feedKey, valueStr, payload);
            } else if (SensorType.isSensor(feedKey)) {
                saveSensorData(user, groupKey, feedKey, valueStr, payload);
            } else {
                log.warn("⚠️ Unrecognized feedKey: {}", feedKey);
            }
            //webSocketSessionManager.sendToUser(user.getId(), payload);
        });
    }

    public MqttPahoMessageHandler createMessageHandler(User user, MqttPahoClientFactory mqttPahoClientFactory) {
        String clientId = "mqtt-handler-" + user.getId();
        MqttPahoMessageHandler handler = new MqttPahoMessageHandler(clientId, mqttPahoClientFactory);
        handler.setAsync(true);
        handler.setConverter(new DefaultPahoMessageConverter());
        return handler;
    }
}
