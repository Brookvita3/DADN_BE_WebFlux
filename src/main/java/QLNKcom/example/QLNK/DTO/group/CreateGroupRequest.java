package QLNKcom.example.QLNK.DTO.group;

import QLNKcom.example.QLNK.model.adafruit.Feed;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class CreateGroupRequest {

    @JsonProperty("name")
    @NotBlank
    private String name;

    @JsonProperty("description")
    private String description;

    @JsonProperty("feeds")
    private List<Feed> feeds;
}
