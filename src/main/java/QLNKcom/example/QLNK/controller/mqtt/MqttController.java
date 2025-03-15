package QLNKcom.example.QLNK.controller.mqtt;

import QLNKcom.example.QLNK.config.jwt.JwtUtils;
import QLNKcom.example.QLNK.exception.CustomAuthException;
import QLNKcom.example.QLNK.response.ResponseObject;
import QLNKcom.example.QLNK.service.mqtt.MqttService;
import QLNKcom.example.QLNK.service.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/mqtt")
public class MqttController {

    private final MqttService mqttService;
    private final UserService userService;
    private final JwtUtils jwtUtils;

    @PostMapping("/subscribe")
    public Mono<ResponseEntity<ResponseObject>> subscribe(ServerHttpRequest request) {
        return Mono.defer(() -> {
            try {
                String token = jwtUtils.extractToken(request);
                String email = jwtUtils.extractAccessEmail(token);
                return userService.findByEmail(email)
                        .flatMap(mqttService::subscribeUserFeeds)
                        .thenReturn(ResponseEntity.ok(new ResponseObject("Subscribed successfully!", HttpStatus.OK.value(), null)));
            } catch (CustomAuthException e) {
                return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ResponseObject(e.getMessage(), HttpStatus.UNAUTHORIZED.value(), null)));
            } catch (Exception e) {
                return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(new ResponseObject("Internal Server Error", HttpStatus.INTERNAL_SERVER_ERROR.value(), null)));
            }
        });
    }

    @PostMapping("/unsubscribe")
    public Mono<ResponseEntity<ResponseObject>> unSubscribe(ServerHttpRequest request) {
        return Mono.defer(() -> {
            try {
                String token = jwtUtils.extractToken(request);
                String email = jwtUtils.extractAccessEmail(token);
                return userService.findByEmail(email)
                        .flatMap(mqttService::unsubscribeUserFeeds)
                        .thenReturn(ResponseEntity.ok(new ResponseObject("Unsubscribed successfully!", HttpStatus.OK.value(), null)));
            } catch (CustomAuthException e) {
                return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ResponseObject(e.getMessage(), HttpStatus.UNAUTHORIZED.value(), null)));
            } catch (Exception e) {
                return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(new ResponseObject("Internal Server Error", HttpStatus.INTERNAL_SERVER_ERROR.value(), null)));
            }
        });
    }

}
