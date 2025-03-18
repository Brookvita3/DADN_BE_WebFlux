package QLNKcom.example.QLNK.service.adafruit;

import QLNKcom.example.QLNK.DTO.CreateGroupRequest;
import QLNKcom.example.QLNK.model.adafruit.Feed;
import QLNKcom.example.QLNK.model.adafruit.Group;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class AdafruitService {

    private final WebClient webClient;

    public AdafruitService(@Value("${http.adafruit.url}") String adaUrl) {
        this.webClient = WebClient.builder().baseUrl(adaUrl).build();
    }

    public Mono<List<Feed>> getUserFeeds(String username, String apiKey) {
        return webClient.get()
                .uri("/{username}/feeds/", username)
                .header("X-AIO-Key", apiKey)
                .retrieve()
                .bodyToFlux(Feed.class)
                .collectList()
                .doOnNext(feeds -> log.info("Fetched {} feeds for user {}", feeds.size(), username));
    }

    public Mono<List<Group>> getUserGroups(String username, String apiKey) {
        return webClient.get()
                .uri("/{username}/groups/", username)
                .header("X-AIO-Key", apiKey)
                .retrieve()
                .bodyToFlux(Group.class)
                .collectList()
                .doOnNext(groups -> log.info("Fetched {} groups for user {}", groups.size(), username));
    }

    public Mono<List<Group>> createUserFeed(String username, String apiKey) {
        return webClient.get()
                .uri("/{username}/groups/", username)
                .header("X-AIO-Key", apiKey)
                .retrieve()
                .bodyToFlux(Group.class)
                .collectList()
                .doOnNext(groups -> log.info("Fetched {} groups for user {}", groups.size(), username));
    }

    public Mono<Group> createUserGroup(String username, String apiKey, CreateGroupRequest groupRequest) {

        Map<String, Object> requestBody = Map.of(
                "group", Map.of(
                        "name", groupRequest.getName(),
                        "description", groupRequest.getDescription()
                )
        );

        return webClient.post()
                .uri("/{username}/groups", username)
                .header("X-AIO-Key", apiKey)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Group.class)
                .doOnNext(group -> log.info("✅ Created group: {} for user {}", group.getName(), username));

    }
}
