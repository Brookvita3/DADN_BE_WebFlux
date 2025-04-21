package QLNKcom.example.QLNK.DTO.user;

import QLNKcom.example.QLNK.enums.ScheduleType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

@Data
public class CreateScheduleRequest {
    @NotNull(message = "Value is required")
    @PositiveOrZero
    private Double value;

    @NotNull(message = "Type is required")
    private ScheduleType type;

    @NotBlank(message = "Time is required")
    private String time;

    private String note;

    private Integer day;

    private Integer dayOfWeek;

    @AssertTrue(message = "Day or dayOfWeek must be provided based on type")
    public boolean isValid() {
        if (type == null) {
            return false; // Nếu type null thì fail luôn
        }
        return switch (type) {
            case WEEKLY -> dayOfWeek != null && day == null;
            case ONCE, MONTHLY -> day != null && dayOfWeek == null;
            case DAILY -> day == null && dayOfWeek == null;
            default -> false;
        };
    }
}
