package QLNKcom.example.QLNK.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class CreateFeedRuleRequest {
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

    @NotBlank(message = "Value for above ceiling must not be blank")
    private String aboveValue;

    @NotBlank(message = "Value for below floor must not be blank")
    private String belowValue;
}
