package QLNKcom.example.QLNK.model.adafruit;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class Feed {
    private String id;

    @JsonProperty("key")
    private String name;
}
