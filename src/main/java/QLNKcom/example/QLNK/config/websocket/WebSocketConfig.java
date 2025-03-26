package QLNKcom.example.QLNK.config.websocket;

import QLNKcom.example.QLNK.exception.CustomAuthException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.server.support.HandshakeWebSocketService;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;
import org.springframework.web.reactive.socket.server.upgrade.ReactorNettyRequestUpgradeStrategy;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;

@Configuration
public class WebSocketConfig {

    @Bean
    public HandlerMapping webSocketMapping(MqttReactiveWebSocketHandler handler) {
        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
        mapping.setOrder(1);
        mapping.setUrlMap(Map.of("/ws", handler));
        return mapping;
    }

    @Bean
    public WebSocketHandlerAdapter webSocketHandlerAdapter() {
        ReactorNettyRequestUpgradeStrategy upgradeStrategy = new ReactorNettyRequestUpgradeStrategy();
        HandshakeWebSocketService webSocketService = new HandshakeWebSocketService(upgradeStrategy) {
            @Override
            public Mono<Void> handleRequest(ServerWebExchange exchange, WebSocketHandler webSocketHandler) {
                if (webSocketHandler instanceof MqttReactiveWebSocketHandler mqttHandler) {
                    return mqttHandler.handleHandshake(exchange)
                            .then(super.handleRequest(exchange, webSocketHandler))
                            .onErrorResume(throwable -> {
                                if (throwable instanceof CustomAuthException customAuthException) {
                                    exchange.getResponse().setStatusCode(customAuthException.getHttpStatus());
                                    return exchange.getResponse().setComplete();
                                }
                                return Mono.error(throwable);
                            });
                }
                return super.handleRequest(exchange, webSocketHandler);
            }
        };
        return new WebSocketHandlerAdapter(webSocketService);
    }
}
