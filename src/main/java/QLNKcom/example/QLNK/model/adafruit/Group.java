package QLNKcom.example.QLNK.model.adafruit;

import lombok.Data;

import java.util.List;

@Data
public class Group {
    private Long id;

    private String name;

    private String key;

    private List<Feed> feeds;
}
