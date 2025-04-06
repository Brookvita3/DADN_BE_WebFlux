package QLNKcom.example.QLNK.model.data;

import QLNKcom.example.QLNK.enums.ScheduleType;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "schedules")
@Data
@Builder
public class Schedule {
    @Id
    private String id;
    private String userId;
    private String fullFeedKey;
    private Double value;
    private ScheduleType type;
    private String time;
    private Integer day;
    private Integer dayOfWeek;
    private String jobKey;
    private String note;
}
