package QLNKcom.example.QLNK.exception;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
public class AdafruitException extends Exception{
    private final HttpStatus httpStatus;
    public AdafruitException(String msg, HttpStatus httpStatus) {
        super(msg);
        this.httpStatus = httpStatus;
    }
}
