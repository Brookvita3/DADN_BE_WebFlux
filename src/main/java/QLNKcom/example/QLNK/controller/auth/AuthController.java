package QLNKcom.example.QLNK.controller.auth;

import QLNKcom.example.QLNK.DTO.LoginRequest;
import QLNKcom.example.QLNK.DTO.RefreshRequest;
import QLNKcom.example.QLNK.DTO.RegisterRequest;
import QLNKcom.example.QLNK.response.ResponseObject;
import QLNKcom.example.QLNK.service.auth.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class AuthController {
    private final AuthService authService;
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    @PostMapping("/login")
    public Mono<ResponseEntity<ResponseObject>> login(@Valid @RequestBody LoginRequest request) {
        return authService.authenticate(request)
                .map(authResponse -> ResponseObject.builder()
                        .message("Login successfully")
                        .data(authResponse)
                        .status(HttpStatus.OK.value())
                        .build())
                .map(ResponseEntity::ok);
    }

    @PostMapping("/register")
    public Mono<ResponseEntity<ResponseObject>> register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request)
                .then(Mono.fromCallable(() -> {
                    ResponseObject response = ResponseObject.builder()
                            .message("Register successfully")
                            .data("Register successfully")
                            .status(HttpStatus.OK.value())
                            .build();
                    return ResponseEntity.ok(response);
                }));
    }

    @PostMapping("/refresh")
    public Mono<ResponseEntity<ResponseObject>> refresh(@RequestBody RefreshRequest request) {
        return authService.refresh(request)
                .map(authResponse -> {
                    ResponseObject response = ResponseObject.builder()
                            .message("Refresh token successfully")
                            .data(authResponse) // <-- Chứa authResponse
                            .status(HttpStatus.OK.value())
                            .build();
                    return ResponseEntity.ok(response);
                });
    }

    @PostMapping("/logout")
    public Mono<ResponseEntity<ResponseObject>> logout(ServerHttpRequest request) {
        return authService.logout(request)
                .then(Mono.fromCallable(() -> {
                    ResponseObject response = ResponseObject.builder()
                            .message("Logout successfully")
                            .data("Logout successfully")
                            .status(HttpStatus.OK.value())
                            .build();
                    return ResponseEntity.ok(response);
                }));
    }



}
