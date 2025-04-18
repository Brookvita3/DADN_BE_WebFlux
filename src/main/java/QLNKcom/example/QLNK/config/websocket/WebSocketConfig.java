package QLNKcom.example.QLNK.config.websocket;

import QLNKcom.example.QLNK.exception.CustomAuthException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
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
@Slf4j
public class WebSocketConfig {

    @Bean
    public HandlerMapping webSocketMapping(MqttReactiveWebSocketHandler handler) {
        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
        mapping.setOrder(1);
        mapping.setUrlMap(Map.of("/ws", handler));
        return mapping;
    }

    @Bean
    public WebSocketHandlerAdapter webSocketHandlerAdapter(MqttReactiveWebSocketHandler mqttHandler) {
        ReactorNettyRequestUpgradeStrategy upgradeStrategy = new ReactorNettyRequestUpgradeStrategy();
        HandshakeWebSocketService webSocketService = new HandshakeWebSocketService(upgradeStrategy) {
            @Override
            public Mono<Void> handleRequest(ServerWebExchange exchange, WebSocketHandler webSocketHandler) {
                log.info("Handling WebSocket handshake for URI: {}", exchange.getRequest().getURI());
                if (webSocketHandler instanceof MqttReactiveWebSocketHandler) {
                    return mqttHandler.handleHandshake(exchange)
                            .flatMap(authentication -> {
                                log.info("Handshake successful, principal: {}", authentication.getName());
                                // Gọi handler và lưu authentication vào session attributes
                                return super.handleRequest(exchange, session -> {
                                    log.info("Storing authentication in session for email:  {}", authentication.getName());
                                    session.getAttributes().put("websocket.auth", authentication);
                                    return webSocketHandler.handle(session);
                                });
                            })
                            .onErrorResume(throwable -> {
                                log.error("Handshake failed: {}", throwable.getMessage(), throwable);
                                if (throwable instanceof CustomAuthException customAuthException) {
                                    exchange.getResponse().setStatusCode(customAuthException.getHttpStatus());
                                    return exchange.getResponse().setComplete();
                                }
                                exchange.getResponse().setStatusCode(HttpStatus.BAD_REQUEST);
                                return exchange.getResponse().setComplete();
                            });
                }
                return super.handleRequest(exchange, webSocketHandler);
            }
        };
        return new WebSocketHandlerAdapter(webSocketService);
    }
}
