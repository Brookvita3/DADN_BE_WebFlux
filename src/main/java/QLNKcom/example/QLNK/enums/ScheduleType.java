package QLNKcom.example.QLNK.enums;

import lombok.Getter;

@Getter
public enum ScheduleType {
    ONCE("ONCE"),
    DAILY("DAILY"),
    WEEKLY("WEEKLY"),
    MONTHLY("MONTHLY");

    private final String type;

    ScheduleType(String type) { this.type = type; }
}
