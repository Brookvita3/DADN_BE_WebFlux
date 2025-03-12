package QLNKcom.example.QLNK.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;

@Getter
public class CustomAuthException extends AuthenticationException {
    private final HttpStatus httpStatus;
    public CustomAuthException(String msg, HttpStatus httpStatus) {
        super(msg);
        this.httpStatus = httpStatus;
    }
}
