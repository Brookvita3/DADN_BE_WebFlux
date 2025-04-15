package QLNKcom.example.QLNK.controller.feed;

import QLNKcom.example.QLNK.DTO.feed.CreateFeedRequest;
import QLNKcom.example.QLNK.DTO.feed.UpdateFeedRequest;
import QLNKcom.example.QLNK.DTO.user.CreateScheduleRequest;
import QLNKcom.example.QLNK.response.ResponseObject;
import QLNKcom.example.QLNK.service.scheduler.ScheduleService;
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
@RequestMapping("${webapp.version}/user/groups")
@RequiredArgsConstructor
public class FeedController {

    private final UserService userService;
    private final ScheduleService scheduleService;

    @PostMapping("/{groupKey}/feeds")
    public Mono<ResponseEntity<ResponseObject>> createFeed(
            @PathVariable String groupKey,
            @RequestBody CreateFeedRequest request) {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(Authentication::getName)
                .flatMap(email -> userService.createFeedForGroup(request, email, groupKey))
                .map(feed -> ResponseEntity.ok(
                        ResponseObject.builder()
                                .message("Feed created and update subscribe successfully")
                                .data(feed)
                                .status(HttpStatus.OK.value())
                                .build()
                ));
    }


    @DeleteMapping("/{groupKey}/feeds/{fullFeedKey}")
    public Mono<ResponseEntity<ResponseObject>> deleteFeed(
            @PathVariable String groupKey, @PathVariable String fullFeedKey) {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(Authentication::getName)
                .flatMap(email -> userService.deleteFeed(email, groupKey, fullFeedKey))
                .thenReturn(ResponseEntity.ok(
                        ResponseObject.builder()
                                .message("Feed delete with schedule and rule, unsubscribe successfully")
                                .data(null)
                                .status(HttpStatus.OK.value())
                                .build()
                ));
    }

    @PutMapping("/{groupKey}/feeds/{fullFeedKey}")
    public Mono<ResponseEntity<ResponseObject>> updateFeed(
            @PathVariable String groupKey, @PathVariable String fullFeedKey, @RequestBody @Valid UpdateFeedRequest request) {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(Authentication::getName)
                .flatMap(email -> userService.updateFeedForGroup(email, groupKey, fullFeedKey, request))
                .map(feed -> ResponseEntity.ok(
                        ResponseObject.builder()
                                .message("Update feed successfully")
                                .data(feed)
                                .status(HttpStatus.OK.value())
                                .build()
                ));
    }

    @PostMapping("/{groupKey}/feeds/{fullFeedKey}/scheduler")
    public Mono<ResponseEntity<ResponseObject>> createSchedule(
            @PathVariable String fullFeedKey,
            @RequestBody @Valid CreateScheduleRequest request) {
        return ReactiveSecurityContextHolder.getContext()
                .map(context -> context.getAuthentication().getName())
                .flatMap(email -> scheduleService.createSchedule(email, fullFeedKey, request))
                .map(schedule -> ResponseEntity.ok(
                        ResponseObject.builder()
                                .message("Schedule created successfully")
                                .status(HttpStatus.OK.value())
                                .data(schedule)
                                .build()
                ))
                .onErrorResume(Exception.class, e -> Mono.just(
                        ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                .body(ResponseObject.builder()
                                        .message("Failed to create schedule: " + e.getMessage())
                                        .status(HttpStatus.BAD_REQUEST.value())
                                        .build())
                ));
    }

    @GetMapping("/{groupKey}/feeds/{fullFeedKey}/scheduler")
    public Mono<ResponseEntity<ResponseObject>> getFeedSchedules(
            @PathVariable String fullFeedKey) {
        return ReactiveSecurityContextHolder.getContext()
                .map(context -> context.getAuthentication().getName())
                .flatMap(email -> scheduleService.getSchedulesByEmailAndFullFeedKey(email, fullFeedKey)
                        .collectList()
                        .map(schedules -> ResponseEntity.ok(
                                ResponseObject.builder()
                                        .message("Schedules retrieved successfully")
                                        .status(HttpStatus.OK.value())
                                        .data(schedules)
                                        .build()
                                )))
                .onErrorResume(Exception.class, e -> Mono.just(
                        ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                .body(ResponseObject.builder()
                                        .message("Failed to retrieve schedules: " + e.getMessage())
                                        .status(HttpStatus.BAD_REQUEST.value())
                                        .build())
                ));
    }

    @DeleteMapping("/{groupKey}/feeds/{fullFeedKey}/scheduler")
    public Mono<ResponseEntity<ResponseObject>> deleteFeedSchedules(
            @RequestParam String id) {
        return scheduleService.deleteSchedulesById(id)
                .thenReturn(ResponseEntity.ok(
                        ResponseObject.builder()
                                .message("Schedules delete successfully")
                                .status(HttpStatus.OK.value())
                                .data(null)
                                .build()
                ))
                .onErrorResume(Exception.class, e -> Mono.just(
                        ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                .body(ResponseObject.builder()
                                        .message("Failed to retrieve schedules: " + e.getMessage())
                                        .status(HttpStatus.BAD_REQUEST.value())
                                        .build())
                ));
    }
}
