package QLNKcom.example.QLNK.controller.group;


import QLNKcom.example.QLNK.DTO.group.CreateGroupRequest;
import QLNKcom.example.QLNK.DTO.feed.UpdateGroupRequest;
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
public class GroupController {

    private final UserService userService;

    @GetMapping("/groups")
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

    @PostMapping("/groups")
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

    @PutMapping("/groups/{groupKey}")
    public Mono<ResponseEntity<ResponseObject>> updateGroup(@PathVariable String groupKey, @RequestBody @Valid UpdateGroupRequest request) {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(Authentication::getName)
                .flatMap(email -> userService.updateGroup(email, groupKey, request))
                .map(group -> ResponseEntity.ok(
                        ResponseObject.builder()
                                .message("Update group successfully")
                                .data(group)
                                .status(HttpStatus.OK.value())
                                .build()
                ));
    }

    @DeleteMapping("/groups/{groupKey}")
    public Mono<ResponseEntity<ResponseObject>> deleteGroup(@PathVariable String groupKey) {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(Authentication::getName)
                .flatMap(email -> userService.deleteGroup(email, groupKey))
                .thenReturn( ResponseEntity.ok(
                        ResponseObject.builder()
                                .message("Group and its feeds deleted successfully")
                                .data(null)
                                .status(HttpStatus.OK.value())
                                .build()
                ));
    }



}
