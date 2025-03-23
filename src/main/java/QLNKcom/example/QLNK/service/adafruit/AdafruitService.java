package QLNKcom.example.QLNK.service.adafruit;

import QLNKcom.example.QLNK.DTO.CreateFeedRequest;
import QLNKcom.example.QLNK.DTO.CreateGroupRequest;
import QLNKcom.example.QLNK.exception.CustomAuthException;
import QLNKcom.example.QLNK.model.adafruit.Feed;
import QLNKcom.example.QLNK.model.adafruit.Group;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
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

    public Mono<Feed> createFeed(String username, String apiKey, String groupKey, CreateFeedRequest request) {
        return webClient.post()
                .uri("/{username}/feeds?group_key={groupKey}", username, groupKey)
                .header("X-AIO-Key", apiKey)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Feed.class);
    }

    public  Mono<Void> deleteFeed(String username, String apiKey, String feedKey) {
        return webClient.delete()
                .uri("/{username}/feeds/{feed_key}", username, feedKey)
                .header("X-AIO-Key", apiKey)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, response -> {
                    if (response.statusCode() == HttpStatus.NOT_FOUND) {
                        return Mono.error(new CustomAuthException("Feed not found on Adafruit", HttpStatus.NOT_FOUND));
                    } else if (response.statusCode() == HttpStatus.UNAUTHORIZED) {
                        return Mono.error(new CustomAuthException("Invalid API key", HttpStatus.UNAUTHORIZED));
                    }
                    return Mono.error(new CustomAuthException("Failed to delete feed on Adafruit: " + response.statusCode(), HttpStatus.BAD_REQUEST));
                })
                .toBodilessEntity()
                .then();
    }
}
