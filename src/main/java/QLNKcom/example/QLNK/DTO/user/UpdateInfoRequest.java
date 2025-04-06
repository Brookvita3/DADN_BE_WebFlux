package QLNKcom.example.QLNK.DTO.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateInfoRequest {
    @Email(message = "Invalid email format")
    private String email;

    @Size(min = 6, message = "Password must be at least 6 characters long")
    private String oldPassword;

    @Size(min = 6, message = "Password must be at least 6 characters long")
    private String newPassword;

    private String apikey;
}
