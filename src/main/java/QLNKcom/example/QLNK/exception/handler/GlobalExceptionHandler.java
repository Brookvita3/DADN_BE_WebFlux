package QLNKcom.example.QLNK.exception.handler;

import QLNKcom.example.QLNK.exception.CustomAuthException;
import QLNKcom.example.QLNK.response.ResponseObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(CustomAuthException.class)
    public ResponseEntity<ResponseObject> handleAuthException(CustomAuthException ex) {
        return ResponseEntity.status(ex.getHttpStatus()).body(
                ResponseObject.builder()
                        .message(ex.getMessage())
                        .status(ex.getHttpStatus().value())
                        .data(null)
                        .build()
        );
    }
}
