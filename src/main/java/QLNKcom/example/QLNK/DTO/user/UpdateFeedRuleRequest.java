package QLNKcom.example.QLNK.DTO.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class UpdateFeedRuleRequest {
    @NotBlank(message = "Input feed must not be blank")
    private String inputFeed;

    @NotNull(message = "Ceiling must not be null")
    @Positive(message = "Ceiling must be a positive number")
    private Double ceiling;

    @NotNull(message = "Floor must not be null")
    @Positive(message = "Floor must be a positive number")
    private Double floor;

    @NotBlank(message = "Output feed for above ceiling must not be blank")
    private String outputFeedAbove;

    @NotBlank(message = "Output feed for below floor must not be blank")
    private String outputFeedBelow;

    @NotNull(message = "Value for above ceiling must not be blank")
    @Positive(message = "AboveValue must be a positive number")
    private Double aboveValue;

    @NotNull(message = "Value for below floor must not be blank")
    @Positive(message = "BelowValue must be a positive number")
    private Double belowValue;
}
