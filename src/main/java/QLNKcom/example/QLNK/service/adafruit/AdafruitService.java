package QLNKcom.example.QLNK.service.adafruit;

import QLNKcom.example.QLNK.DTO.feed.CreateFeedRequest;
import QLNKcom.example.QLNK.DTO.group.CreateGroupRequest;
import QLNKcom.example.QLNK.DTO.feed.UpdateFeedRequest;
import QLNKcom.example.QLNK.DTO.feed.UpdateGroupRequest;
import QLNKcom.example.QLNK.exception.CustomAuthException;
import QLNKcom.example.QLNK.model.adafruit.Feed;
import QLNKcom.example.QLNK.model.adafruit.Group;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class AdafruitService {

    private final WebClient webClient;

    public AdafruitService(@Value("${http.adafruit.url}") String adaUrl) {
        this.webClient = WebClient.builder()
                .baseUrl(adaUrl)
                .filter(ExchangeFilterFunction.ofResponseProcessor(this::handleResponseErrors))
                .build();
    }

    private Mono<ClientResponse> handleResponseErrors(ClientResponse response) {
        if (response.statusCode().is4xxClientError() || response.statusCode().is5xxServerError()) {
            return response.bodyToMono(String.class)
                    .defaultIfEmpty("No error details provided")
                    .flatMap(body -> {
                        HttpStatus status = HttpStatus.valueOf(response.statusCode().value());
                        String message;
                        if (status == HttpStatus.NOT_FOUND) {
                            message = "Resource not found on Adafruit: " + body;
                        } else if (status == HttpStatus.UNAUTHORIZED) {
                            message = "Invalid API key: " + body;
                        } else if (response.statusCode().is5xxServerError()) {
                            message = "Adafruit server error (" + status + "): " + body;
                        } else {
                            message = "Client error from Adafruit (" + status + "): " + body;
                        }
                        return Mono.error(new CustomAuthException(message, status));
                    });
        }
        return Mono.just(response);
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
                .bodyToMono(Feed.class)
                .doOnSuccess(feed -> log.info("✅ Created feed {} on Adafruit for group {}", feed.getKey(), groupKey));
    }

    public Mono<Void> deleteFeed(String username, String apiKey, String feedKey) {
        return webClient.delete()
                .uri("/{username}/feeds/{feed_key}", username, feedKey)
                .header("X-AIO-Key", apiKey)
                .retrieve()
                .toBodilessEntity()
                .doOnSuccess(voidResponseEntity -> log.info("✅ Delete feed successfully on adafruit"))
                .then();
    }

    public Mono<Void> deleteGroup(String username, String apiKey, String groupKey) {
        return webClient.delete()
                .uri("/{username}/groups/{group_key}", username, groupKey)
                .header("X-AIO-Key", apiKey)
                .retrieve()
                .toBodilessEntity()
                .doOnSuccess(voidResponseEntity -> log.info("✅ Delete group successfully on adafruit"))
                .then();
    }

    public Mono<Void> updateGroup(String username, String apiKey, String oldGroupKey, UpdateGroupRequest request) {

        String formatKey = request.getKey() != null ? request.getKey().replace(" ", "-") : null;

        Map<String, Object> updateBody = new HashMap<>();
        if (request.getName() != null) {
            updateBody.put("name", request.getName());
        }
        if (request.getDescription() != null) {
            updateBody.put("description", request.getDescription());
        }
        if (formatKey != null) {
            updateBody.put("key", formatKey);
        }

        if (updateBody.isEmpty()) {
            return Mono.empty();
        }

        log.info("body request to ada: {}", updateBody);

        return webClient.put()
                .uri("/{username}/groups/{groupKey}", username, oldGroupKey)
                .header("X-AIO-Key", apiKey)
                .bodyValue(updateBody)
                .retrieve()
                .toBodilessEntity()
                .doOnSuccess(voidResponseEntity -> log.info("✅ Update group successfully on adafruit"))
                .then();
    }

    public Mono<Feed> updateFeed(String username, String apiKey, String groupKey, String oldFullFeedKey, UpdateFeedRequest request) {
        String feedKey = request.getKey();
        Map<String, Object> requestBody = Map.of(
                "name", request.getName(),
                "description", request.getDescription(),
                "key", feedKey
        );

        return webClient.put()
                .uri("/{username}/feeds/{feedKey}", username, oldFullFeedKey)
                .header("X-AIO-Key", apiKey)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Feed.class)
                .map(feed -> {
                    feed.setKey(feedKey);
                    return feed;
                })
                .doOnSuccess(feed -> log.info("✅ Update feed {} on Adafruit for group {}", feed.getKey(), groupKey));
    }
}
