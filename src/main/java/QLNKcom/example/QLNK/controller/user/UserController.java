package QLNKcom.example.QLNK.controller.user;

import QLNKcom.example.QLNK.DTO.user.*;
import QLNKcom.example.QLNK.response.ResponseObject;
import QLNKcom.example.QLNK.service.auth.AuthService;
import QLNKcom.example.QLNK.service.user.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("${webapp.version}/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AuthService authService;

    @PostMapping("/rule")
    public Mono<ResponseEntity<ResponseObject>> createFeedRule(@RequestBody @Valid CreateFeedRuleRequest request) {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(Authentication::getName)
                .flatMap(email -> userService.createFeedRule(email, request))
                .map(feedRule -> ResponseEntity.ok(
                        ResponseObject.builder()
                                .message("Create feed rule successfully")
                                .data(feedRule)
                                .status(HttpStatus.OK.value())
                                .build()
                ));
    }

    @GetMapping("/rule")
    public Mono<ResponseEntity<ResponseObject>> getFeedRules(
            @RequestParam(value = "feedname", required = false) String feedName) {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(Authentication::getName)
                .flatMap(email -> userService.getFeedRules(email, feedName).collectList())
                .map(feedRules -> ResponseEntity.ok(
                        ResponseObject.builder()
                                .message("Get feed rule successfully")
                                .data(feedRules)
                                .status(HttpStatus.OK.value())
                                .build()
                ));
    }

    @GetMapping("/info")
    public Mono<ResponseEntity<ResponseObject>> getInfo() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(Authentication::getName)
                .flatMap(userService::getInfo)
                .map(user -> ResponseEntity.ok(
                        ResponseObject.builder()
                                .message("Get info successfully")
                                .data(user)
                                .status(HttpStatus.OK.value())
                                .build()
                ));
    }

    @PutMapping("/info")
    public Mono<ResponseEntity<ResponseObject>> updateInfo(@RequestBody @Valid UpdateInfoRequest request) {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(Authentication::getName)
                .flatMap(email -> userService.updateInfo(email, request))
                .map(feedRule -> ResponseEntity.ok(
                        ResponseObject.builder()
                                .message("Update info successfully")
                                .data(feedRule)
                                .status(HttpStatus.OK.value())
                                .build()
                ));
    }

    @PutMapping("/rule")
    public Mono<ResponseEntity<ResponseObject>> updateFeedRule(
            @RequestBody @Valid UpdateFeedRuleRequest request,
            @RequestParam("feed") String fullFeedKey) {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(Authentication::getName)
                .flatMap(email -> userService.updateFeedRule(email, fullFeedKey, request))
                .map(feedRule -> ResponseEntity.ok(
                        ResponseObject.builder()
                                .message("Update feed rule rule successfully")
                                .data(feedRule)
                                .status(HttpStatus.OK.value())
                                .build()
                ));
    }

    @PostMapping("/forgot-password")
    public Mono<ResponseEntity<ResponseObject>> forgotPassword(@RequestBody @Valid ForgotPasswordRequest request) {
        return authService.sendResetPasswordLink(request.getEmail())
                .thenReturn(ResponseEntity.ok(
                        ResponseObject.builder()
                                .message("Reset password link sent to your email")
                                .status(HttpStatus.OK.value())
                                .build()
                ))
                .onErrorResume(Exception.class, e -> Mono.just(
                        ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                .body(ResponseObject.builder()
                                        .message("Failed to send reset link: " + e.getMessage())
                                        .status(HttpStatus.BAD_REQUEST.value())
                                        .build())
                ));
    }

    @PostMapping("/reset-password")
    public Mono<ResponseEntity<ResponseObject>> resetPassword(@RequestBody @Valid ResetPasswordRequest request) {
        return authService.resetPasswordWithToken(request.getToken(), request.getNewPassword())
                .map(user -> ResponseEntity.ok(
                        ResponseObject.builder()
                                .message("Password reset successfully")
                                .status(HttpStatus.OK.value())
                                .build()
                ))
                .onErrorResume(Exception.class, e -> Mono.just(
                        ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                .body(ResponseObject.builder()
                                        .message("Failed to reset password: " + e.getMessage())
                                        .status(HttpStatus.BAD_REQUEST.value())
                                        .build())
                ));
    }

}
