package QLNKcom.example.QLNK.service.scheduler;

import QLNKcom.example.QLNK.service.mqtt.MqttService;
import lombok.RequiredArgsConstructor;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SendValueJob implements Job {

    private final MqttService mqttService;

    @Override
    public void execute(JobExecutionContext context) {
        String email = context.getJobDetail().getJobDataMap().getString("userId");
        String fullFeedKey = context.getJobDetail().getJobDataMap().getString("fullFeedKey");
        Double value = context.getJobDetail().getJobDataMap().getDouble("value");

        // Gửi qua MQTT với 3 tham số kiểu String
        mqttService.sendMqttCommand(email, fullFeedKey, String.valueOf(value))
                .doOnSuccess(v -> System.out.println("Sent value: " + value + " to " + fullFeedKey + " for user " + email))
                .subscribe();
    }
}
