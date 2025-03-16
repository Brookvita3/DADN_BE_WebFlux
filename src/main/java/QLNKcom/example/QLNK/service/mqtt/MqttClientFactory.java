package QLNKcom.example.QLNK.service.mqtt;

import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.stereotype.Component;

@Component
public class MqttClientFactory {
    @Value("${mqtt.broker.url}")
    private String brokerUrl;

    public MqttPahoClientFactory createMqttClient(String username, String apiKey) {
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();

        MqttConnectOptions mqttConnectOptions = new MqttConnectOptions();
        mqttConnectOptions.setServerURIs(new String[]{brokerUrl});
        mqttConnectOptions.setUserName(username);
        mqttConnectOptions.setPassword(apiKey.toCharArray());
        mqttConnectOptions.setKeepAliveInterval(60);

        factory.setConnectionOptions(mqttConnectOptions);
        return factory;
    }
}
