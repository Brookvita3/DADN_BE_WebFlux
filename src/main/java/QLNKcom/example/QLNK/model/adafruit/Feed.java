package QLNKcom.example.QLNK.model.adafruit;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Feed {
    private Long id;

    private String name;

    private String key;

    private String description;
}
