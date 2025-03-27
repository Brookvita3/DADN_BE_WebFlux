package QLNKcom.example.QLNK.enums;

import lombok.Getter;

import java.util.Arrays;

@Getter
public enum DeviceType {
    FAN("fan"),
    PUMP("pump");

    private final String type;

    DeviceType(String type) {
        this.type = type;
    }

    public static boolean isDevice(String fullFeedKey) {
        String deviceType = fullFeedKey.split("\\.")[1];
        return Arrays.stream(DeviceType.values())
                .anyMatch(device -> device.type.equals(deviceType));
    }
}
