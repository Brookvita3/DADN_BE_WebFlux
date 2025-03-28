package QLNKcom.example.QLNK.DTO;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UpdateFeedRequest {

    @JsonProperty("name")
    @NotNull(message = "Name is required")
    private String name;

    @JsonProperty("description")
    @NotNull(message = "Description is required")
    private String description;

    @JsonProperty("key")
    @NotNull(message = "Key is required")
    private String key;

}
