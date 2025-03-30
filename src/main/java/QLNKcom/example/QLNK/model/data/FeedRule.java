package QLNKcom.example.QLNK.model.data;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "feedRule")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedRule {
    @Id
    private String id;

    private String groupKey;
    private String inputFeed; // cay-1.temp
    private Double ceiling;
    private Double floor;
    private String outputFeedAbove;
    private String outputFeedBelow;
    private String aboveValue;
    private String belowValue;
    private String email;

    private Long lastViolationTime;
    private Boolean continuousViolation;
}
