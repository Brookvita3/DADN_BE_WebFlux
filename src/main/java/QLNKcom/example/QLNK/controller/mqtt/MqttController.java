package QLNKcom.example.QLNK.controller.mqtt;

import QLNKcom.example.QLNK.config.jwt.JwtUtils;
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
        return jwtUtils.extractToken(request)
                .flatMap(jwtUtils::extractAccessEmail)
                .flatMap(userService::findByEmail)
                .flatMap(mqttService::subscribeFeed)
                .thenReturn(new ResponseObject("Subscribed successfully!", HttpStatus.OK.value(), null))
                .map(response -> ResponseEntity.ok().body(response));
    }
}
