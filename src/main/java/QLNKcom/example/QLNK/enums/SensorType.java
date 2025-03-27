package QLNKcom.example.QLNK.enums;

import lombok.Getter;

import java.util.Arrays;

@Getter
public enum SensorType {
    TEMPERATURE("temp"),
    HUMIDITY("hum"),
    LIGHT("light");

    private final String type;

    SensorType(String type) {
        this.type = type;
    }

    public static boolean isSensor(String fullFeedKey) {
        String sensorType = fullFeedKey.split("\\.")[1];
        return Arrays.stream(SensorType.values())
                .anyMatch(sensor -> sensor.type.equals(sensorType));
    }
}
