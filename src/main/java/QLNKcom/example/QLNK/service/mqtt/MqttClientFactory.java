package QLNKcom.example.QLNK.service.mqtt;

import lombok.RequiredArgsConstructor;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MqttClientFactory {
    @Value("${mqtt.broker.url}")
    private String brokerUrl;

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
