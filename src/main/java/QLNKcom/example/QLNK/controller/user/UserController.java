package QLNKcom.example.QLNK.controller.user;

import QLNKcom.example.QLNK.DTO.CreateFeedRequest;
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
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me/groups")
    public Mono<ResponseEntity<ResponseObject>> getGroups() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(Authentication::getName)
                .flatMap(userService::getAllGroupsByEmail)
                .map(groups -> ResponseEntity.ok(
                        ResponseObject.builder()
                                .message("Get user groups successfully")
                                .data(groups)
                                .status(HttpStatus.OK.value())
                                .build()
                ));
    }

    @PostMapping("/me/groups")
    public Mono<ResponseEntity<ResponseObject>> createGroup(@RequestBody @Valid CreateGroupRequest request) {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(Authentication::getName)
                .flatMap(email -> userService.createGroupByEmail(request, email))
                .map(group -> ResponseEntity.ok(
                        ResponseObject.builder()
                                .message("Create group successfully")
                                .data(group)
                                .status(HttpStatus.OK.value())
                                .build()
                ));
    }

    @PostMapping("/me/groups/{groupKey}/feeds")
    public Mono<ResponseEntity<ResponseObject>> createFeed(
            @PathVariable String groupKey,
            @RequestBody CreateFeedRequest request) {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(Authentication::getName)
                .flatMap(email -> userService.createFeedForGroup(request, email, groupKey))
                .map(feed -> ResponseEntity.ok(
                        ResponseObject.builder()
                                .message("Feed created successfully")
                                .data(feed)
                                .status(HttpStatus.OK.value())
                                .build()
                ));
    }


}
