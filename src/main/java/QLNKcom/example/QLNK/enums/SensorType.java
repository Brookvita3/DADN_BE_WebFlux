package QLNKcom.example.QLNK.enums;

import lombok.Getter;

import java.util.Arrays;

@Getter
public enum SensorType {
    TEMPERATURE("temp"),
    HUMIDITY("hum"),
    LIGHT("light");

    private final String feedKey;

    SensorType(String feedKey) {
        this.feedKey = feedKey;
    }

    public static boolean isSensor(String feedKey) {
        return Arrays.stream(SensorType.values())
                .anyMatch(sensor -> sensor.feedKey.equals(feedKey));
    }
}
