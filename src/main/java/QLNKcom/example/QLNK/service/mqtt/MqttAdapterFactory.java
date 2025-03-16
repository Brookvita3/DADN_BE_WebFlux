package QLNKcom.example.QLNK.service.mqtt;

import QLNKcom.example.QLNK.model.User;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MqttAdapterFactory {

    public MqttPahoMessageDrivenChannelAdapter createMqttAdapter(User user, MqttPahoClientFactory client, List<String> feeds) {
        String clientId = "mqtt-" + user.getId();
        MqttPahoMessageDrivenChannelAdapter adapter = new MqttPahoMessageDrivenChannelAdapter(
                clientId, client, feeds.toArray(new String[0])
        );
        adapter.setQos(1);
        adapter.setConverter(new DefaultPahoMessageConverter());
        return adapter;
    }
}
