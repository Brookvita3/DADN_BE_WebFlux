package QLNKcom.example.QLNK.controller.user;

import QLNKcom.example.QLNK.DTO.user.CreateFeedRuleRequest;
import QLNKcom.example.QLNK.DTO.user.UpdateFeedRuleRequest;
import QLNKcom.example.QLNK.DTO.user.UpdateInfoRequest;
import QLNKcom.example.QLNK.response.ResponseObject;
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
}
