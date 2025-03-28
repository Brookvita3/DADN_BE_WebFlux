package QLNKcom.example.QLNK.controller.user;

import QLNKcom.example.QLNK.DTO.CreateFeedRequest;
import QLNKcom.example.QLNK.DTO.CreateFeedRuleRequest;
import QLNKcom.example.QLNK.DTO.CreateGroupRequest;
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

}
