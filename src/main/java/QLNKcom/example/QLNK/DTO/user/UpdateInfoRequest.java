package QLNKcom.example.QLNK.DTO.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateInfoRequest {
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "oldPassword is required")
    @Size(min = 6, message = "Password must be at least 6 characters long")
    private String oldPassword;

    @NotBlank(message = "newPassword is required")
    @Size(min = 6, message = "Password must be at least 6 characters long")
    private String newPassword;

    @NotBlank(message = "ApiKey is required")
    private String apikey;
}
