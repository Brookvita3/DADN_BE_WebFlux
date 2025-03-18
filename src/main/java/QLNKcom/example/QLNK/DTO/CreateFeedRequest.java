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
}
