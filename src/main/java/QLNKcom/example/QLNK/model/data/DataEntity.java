package QLNKcom.example.QLNK.model.data;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Document
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public abstract class DataEntity {
    @Id
    private String id;

    private String username;
    private String groupKey;
    private String feedKey;
    private Instant timeStamp;
}
