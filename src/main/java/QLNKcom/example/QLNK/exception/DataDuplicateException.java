package QLNKcom.example.QLNK.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class DataDuplicateException extends RuntimeException {
    private final HttpStatus status;
    public DataDuplicateException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }
}
