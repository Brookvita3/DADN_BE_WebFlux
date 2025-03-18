package QLNKcom.example.QLNK.model.adafruit;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class Group {
    private Long id;

    private String name;

    private String key;

    private String description;

    private List<Feed> feeds;
}
