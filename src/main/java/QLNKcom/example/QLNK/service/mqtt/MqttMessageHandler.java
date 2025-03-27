package QLNKcom.example.QLNK.service.mqtt;

import QLNKcom.example.QLNK.enums.DeviceType;
import QLNKcom.example.QLNK.enums.SensorType;
import QLNKcom.example.QLNK.model.User;
import QLNKcom.example.QLNK.model.adafruit.Feed;
import QLNKcom.example.QLNK.model.data.DeviceData;
import QLNKcom.example.QLNK.model.data.SensorData;
import QLNKcom.example.QLNK.provider.user.UserProvider;
import QLNKcom.example.QLNK.repository.DeviceDataRepository;
import QLNKcom.example.QLNK.repository.SensorDataRepository;
import QLNKcom.example.QLNK.service.email.EmailService;
import QLNKcom.example.QLNK.service.websocket.WebSocketSessionManager;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler;
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Service
@Slf4j
@RequiredArgsConstructor
public class MqttMessageHandler {

    private final WebSocketSessionManager webSocketSessionManager;
    private final DeviceDataRepository deviceDataRepository;
    private final SensorDataRepository sensorDataRepository;
    private final EmailService emailService;
    private final UserProvider userProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private JsonNode parsePayload(String payload) {
        try {
            return objectMapper.readTree(payload).get("data");
        } catch (JsonProcessingException e) {
            log.error("❌ Failed to parse JSON payload: {}", e.getMessage());
            return null;
        }
    }

    private Mono<Void> saveDeviceData(User user, String groupKey, String feedKey, String valueStr, String payload) {
        boolean status = "1".equals(valueStr) || "true".equalsIgnoreCase(valueStr);
        DeviceData deviceData = DeviceData.builder()
                .username(user.getUsername())
                .groupKey(groupKey)
                .feedKey(feedKey)
                .status(status)
                .timeStamp(Instant.now())
                .build();

        return deviceDataRepository.save(deviceData)
                .doOnSuccess(savedData -> webSocketSessionManager.sendToUser(user.getId(), payload))
                .doOnError(error -> log.error("❌ Error saving Device Data: {}", error.getMessage()))
                .then();
    }

    private Mono<Void> saveSensorData(User user, String groupKey, String feedKey, String valueStr, String payload) {
        try {
            double value = Double.parseDouble(valueStr);
            SensorData sensorData = SensorData.builder()
                    .username(user.getUsername())
                    .groupKey(groupKey)
                    .feedKey(feedKey)
                    .value(value)
                    .timeStamp(Instant.now())
                    .build();

            return sensorDataRepository.save(sensorData)
                    .doOnSuccess(savedData -> webSocketSessionManager.sendToUser(user.getId(), payload))
                    .doOnError(error -> log.error("❌ Error saving Sensor Data: {}", error.getMessage()))
                    .then();
        } catch (NumberFormatException e) {
            log.error("❌ Invalid sensor data: {}", payload);
            return Mono.empty();
        }
    }

    private Mono<Void> saveData(User user, String groupKey, String fullFeedKey, String value, String payload) {
        log.info("fullFeedKey in save data : {}", fullFeedKey);
        if (DeviceType.isDevice(fullFeedKey)) {
            return saveDeviceData(user, groupKey, fullFeedKey, value, payload);
        } else if (SensorType.isSensor(fullFeedKey)) {
            return saveSensorData(user, groupKey, fullFeedKey, value, payload);
        } else {
            log.warn("⚠️ Unrecognized feedKey: {}", fullFeedKey);
            return Mono.empty();
        }
    }

    private Mono<Void> processMessage(User user, String topic, String payload) {
        String[] parts = topic.split("/");
        if (parts.length < 4) {
            log.warn("⚠️ Invalid topic format: {}", topic);
            return Mono.empty();
        }

        String[] groupAndFeed = parts[2].split("\\.");
        if (groupAndFeed.length < 2) {
            log.warn("⚠️ Invalid feed format: {}", parts[2]);
            return Mono.empty();
        }

        String groupKey = groupAndFeed[0];
        String feedKey = groupAndFeed[1];
        String fullFeedKey = groupKey + "." + feedKey;

        return Mono.just(payload)
                .map(this::parsePayload)
                .flatMap(dataNode -> {
                    if (dataNode == null || dataNode.get("value") == null) { // Adjusted for your payload
                        log.warn("⚠️ Missing 'data.value' in payload: {}", payload);
                        return Mono.empty();
                    }

                    String valueStr = dataNode.get("value").asText();
                    double value;
                    try {
                        value = Double.parseDouble(valueStr);
                    } catch (NumberFormatException e) {
                        log.warn("⚠️ Invalid value format in payload: {}", valueStr);
                        return Mono.empty();
                    }
                    Mono<Void> alertMono = userProvider.findFeedByKey(user.getId(), fullFeedKey)
                            .filter(feed -> SensorType.isSensor(fullFeedKey)) // Only proceed if sensor
                            .flatMap(feed -> checkAndAlert(user, feed, value))
                            .switchIfEmpty(Mono.empty())
                            .then();
                    return alertMono.then(saveData(user, groupKey, fullFeedKey, valueStr, payload));
                });
    }

    private Mono<Void> checkAndAlert(User user, Feed feed, double value) {
        Double ceiling = feed.getCeiling();
        Double floor = feed.getFloor();
        String feedName = feed.getName();

        if (ceiling != null && value > ceiling) {
            String subject = "Alert: " + feedName + " Exceeded Upper Threshold";
            String text = String.format("The %s value (%.1f) has exceeded the upper threshold of %.1f.", feed.getKey(), value, ceiling);
            return emailService.sendEmail(user.getEmail(), subject, text)
                    .doOnSuccess(v -> log.info("Sent email alert for {} exceeding ceiling: {} > {}", feedName, value, ceiling));
        } else if (floor != null && value < floor) {
            String subject = "Alert: " + feedName + " Below Lower Threshold";
            String text = String.format("The %s value (%.1f) has fallen below the lower threshold of %.1f.", feed.getKey(), value, floor);
            return emailService.sendEmail(user.getEmail(), subject, text)
                    .doOnSuccess(v -> log.info("Sent email alert for {} below floor: {} < {}", feedName, value, floor));
        }
        return Mono.empty();
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
            assert topic != null;
            processMessage(user, topic, payload).subscribe(
                    v -> {},
                    e -> log.error("Error processing MQTT message from topic {}: {}", topic, e.getMessage())
            );
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
