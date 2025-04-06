package QLNKcom.example.QLNK.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class InvalidPasswordException extends RuntimeException {
    private final HttpStatus httpStatus;
    public InvalidPasswordException(String message, HttpStatus httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
    }
}
