package QLNKcom.example.QLNK.DTO;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateFeedRequest {

    @JsonProperty("name")
    @NotBlank
    private String name;

    @JsonProperty("description")
    private String description;

    @JsonProperty("floor")
    @NotBlank
    private Double floor;

    @JsonProperty("ceiling")
    @NotBlank
    private Double ceiling;
}
