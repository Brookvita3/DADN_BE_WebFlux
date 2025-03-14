package QLNKcom.example.QLNK.model;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "users")
@Data
@Builder
public class User {
    @Id
    private String id;
    private String username;
    private String password;
    private String email;
    private String apikey;
}
