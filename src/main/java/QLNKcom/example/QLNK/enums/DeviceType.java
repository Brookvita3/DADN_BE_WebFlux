package QLNKcom.example.QLNK.enums;

import lombok.Getter;

import java.util.Arrays;

@Getter
public enum DeviceType {
    FAN("fan"),
    PUMP("pump");

    private final String feedKey;

    DeviceType(String feedKey) {
        this.feedKey = feedKey;
    }

    public static boolean isDevice(String feedKey) {
        return Arrays.stream(DeviceType.values())
                .anyMatch(device -> device.feedKey.equals(feedKey));
    }
}
