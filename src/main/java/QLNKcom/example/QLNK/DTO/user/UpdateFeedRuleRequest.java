package QLNKcom.example.QLNK.DTO.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

@Data
public class UpdateFeedRuleRequest {

    private String inputFeed;

    @PositiveOrZero(message = "Ceiling must be a positive number")
    private Double ceiling;

    @PositiveOrZero(message = "Floor must be a positive number")
    private Double floor;

    private String outputFeedAbove;

    private String outputFeedBelow;

    @PositiveOrZero(message = "AboveValue must be a positive number")
    private Double aboveValue;

    @PositiveOrZero(message = "BelowValue must be a positive number")
    private Double belowValue;
}
