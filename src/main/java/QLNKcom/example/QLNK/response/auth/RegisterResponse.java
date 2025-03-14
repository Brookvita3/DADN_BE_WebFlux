package QLNKcom.example.QLNK.response.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RegisterResponse {
    @JsonProperty("email")
    private String email;

    @JsonProperty("password")
    private String password;
}
