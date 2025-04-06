package QLNKcom.example.QLNK.exception.handler;

import QLNKcom.example.QLNK.exception.AdafruitException;
import QLNKcom.example.QLNK.exception.CustomAuthException;
import QLNKcom.example.QLNK.exception.DataNotFoundException;
import QLNKcom.example.QLNK.exception.InvalidPasswordException;
import QLNKcom.example.QLNK.response.ResponseObject;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    @ExceptionHandler(CustomAuthException.class)
    public Mono<ResponseEntity<ResponseObject>> handleAuthException(CustomAuthException ex) {
        log.error("🔥 Caught CustomAuthException: {}", ex.getMessage());
        ResponseObject response = ResponseObject.builder()
                .message(ex.getMessage())
                .status(ex.getHttpStatus().value())
                .data(null)
                .build();

        return Mono.just(ResponseEntity.status(ex.getHttpStatus()).body(response));
    }

    @ExceptionHandler(DataNotFoundException.class)
    public Mono<ResponseEntity<ResponseObject>> handleDataNotFoundException(DataNotFoundException ex) {
        ResponseObject response = ResponseObject.builder()
                .message(ex.getMessage())
                .status(ex.getHttpStatus().value())
                .data(null)
                .build();

        return Mono.just(ResponseEntity.status(ex.getHttpStatus()).body(response));
    }


    @ExceptionHandler(WebExchangeBindException.class)
    public Mono<ResponseEntity<ResponseObject>> handleValidationException(WebExchangeBindException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getFieldErrors().forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));

        ResponseObject response = ResponseObject.builder()
                .message("Validation failed")
                .status(HttpStatus.BAD_REQUEST.value())
                .data(errors) // Trả về danh sách lỗi chi tiết
                .build();

        return Mono.just(ResponseEntity.badRequest().body(response));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public Mono<ResponseEntity<ResponseObject>> handleConstraintViolationException(ConstraintViolationException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getConstraintViolations().forEach(violation ->
                errors.put(violation.getPropertyPath().toString(), violation.getMessage()));

        ResponseObject response = ResponseObject.builder()
                .message("Validation failed")
                .status(HttpStatus.BAD_REQUEST.value())
                .data(errors)
                .build();

        return Mono.just(ResponseEntity.badRequest().body(response));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public Mono<ResponseEntity<ResponseObject>> handleBadCredentialException(Exception ex) {
        ResponseObject response = ResponseObject.builder()
                .message("Invalid email or password")
                .status(HttpStatus.BAD_REQUEST.value())
                .data(ex)
                .build();

        return Mono.just(ResponseEntity.internalServerError().body(response));
    }

    @ExceptionHandler(AdafruitException.class)
    public Mono<ResponseEntity<ResponseObject>> handleAdafruitException(Exception ex) {
        ResponseObject response = ResponseObject.builder()
                .message("Invalid username, please check your username on Adafruit")
                .status(HttpStatus.BAD_REQUEST.value())
                .data(ex)
                .build();

        return Mono.just(ResponseEntity.internalServerError().body(response));
    }

    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<ResponseObject>> handleGeneralException(Exception ex) {
        ResponseObject response = ResponseObject.builder()
                .message("Error in server")
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .data(ex)
                .build();

        return Mono.just(ResponseEntity.internalServerError().body(response));
    }

    @ExceptionHandler(InvalidPasswordException.class)
    public Mono<ResponseEntity<ResponseObject>> handleInvalidPasswordException(InvalidPasswordException ex) {
        ResponseObject response = ResponseObject.builder()
                .message(ex.getMessage())
                .status(ex.getHttpStatus().value())
                .data(null)
                .build();

        return Mono.just(ResponseEntity.status(ex.getHttpStatus()).body(response));
    }
}
