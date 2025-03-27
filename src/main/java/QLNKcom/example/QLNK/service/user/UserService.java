package QLNKcom.example.QLNK.service.user;

import QLNKcom.example.QLNK.DTO.*;
import QLNKcom.example.QLNK.exception.CustomAuthException;
import QLNKcom.example.QLNK.exception.DataNotFoundException;
import QLNKcom.example.QLNK.model.User;
import QLNKcom.example.QLNK.model.adafruit.Feed;
import QLNKcom.example.QLNK.model.adafruit.Group;
import QLNKcom.example.QLNK.provider.user.UserProvider;
import QLNKcom.example.QLNK.service.adafruit.AdafruitService;
import QLNKcom.example.QLNK.service.mqtt.MqttService;
import QLNKcom.example.QLNK.service.mqtt.MqttSubscriptionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserProvider userProvider;
    private final PasswordEncoder passwordEncoder;
    private final AdafruitService adafruitService;
    private final MqttService mqttService;
    private final MqttSubscriptionManager mqttSubscriptionManager;

    private Mono<Boolean> isGroupExists(User user, String groupName) {
        return Mono.just(user.getGroups().stream()
                .anyMatch(group -> group.getName().equals(groupName)));
    }

    private Mono<Boolean> isFeedExists(Group group, String feedName) {
        return Mono.just(group.getFeeds().stream()
                .anyMatch(feed -> feed.getName().equals(feedName)));
    }

    private Mono<Group> saveGroupToDatabase(User user, Group newGroup) {
        return isGroupExists(user, newGroup.getName())
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new CustomAuthException("Group already exists", HttpStatus.BAD_REQUEST));
                    }
                    user.getGroups().add(newGroup);
                    return userProvider.saveUser(user).thenReturn(newGroup);
                });
    }

    private Mono<Group> findGroupByKey(User user, String groupKey) {
        return Mono.justOrEmpty(user.getGroups().stream()
                        .filter(group -> group.getKey().equals(groupKey))
                        .findFirst())
                .switchIfEmpty(Mono.error(new CustomAuthException("Group not found", HttpStatus.NOT_FOUND)));
    }

    private Mono<Feed> saveFeedToDatabase(User user, String groupKey, Feed createdFeed) {
        return findGroupByKey(user, groupKey)
                .flatMap(group -> isFeedExists(group, createdFeed.getName())
                        .flatMap(feedExists -> {
                            if (feedExists) {
                                return Mono.error(new CustomAuthException("Feed already exists in this group", HttpStatus.BAD_REQUEST));
                            }
                            group.getFeeds().add(createdFeed);
                            return userProvider.saveUser(user).thenReturn(createdFeed);
                        })
                );
    }

    public Mono<User> createUser(RegisterRequest request) {
        User newUser = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .apikey(request.getApikey())
                .username(request.getUsername())
                .build();

        return userProvider.saveUser(newUser);
    }

    public Mono<Group> createGroupByEmail(CreateGroupRequest request, String email) {
        return userProvider.findByEmail(email)
                .flatMap(user -> adafruitService.createUserGroup(user.getUsername(), user.getApikey(), request)
                        .onErrorResume(e -> Mono.error(new CustomAuthException("Failed to create group on Adafruit: " + e.getMessage(), HttpStatus.BAD_REQUEST)))
                        .flatMap(adafruitGroup -> saveGroupToDatabase(user, adafruitGroup))
                );
    }

    public Mono<Feed> createFeedForGroup(CreateFeedRequest request, String email, String groupKey) {
        return userProvider.findByEmail(email)
                .flatMap(user -> adafruitService.createFeed(user.getUsername(), user.getApikey(), groupKey, request)
                        .onErrorResume(e -> Mono.error(new CustomAuthException("Failed to create feed on Adafruit: " + e.getMessage(), HttpStatus.BAD_REQUEST)))
                        .flatMap(feed -> saveFeedToDatabase(user, groupKey, feed)
                                .flatMap(savedFeed -> mqttService.updateUserFeedSubscription(user, feed))));
    }

    public Mono<List<Group>> getAllGroupsByEmail(String email) {
        return userProvider.findByEmail(email).map(User::getGroups);
    }

    public Mono<Void> deleteFeed(String email, String groupKey, String feedKey) {
        return userProvider.findByEmail(email)
                .flatMap(user -> {

                    Group group = user.getGroups().stream()
                            .filter(g -> g.getKey().equals(groupKey))
                            .findFirst()
                            .orElseThrow(() -> new DataNotFoundException("Group not found", HttpStatus.NOT_FOUND));

                    Feed feed = group.getFeeds().stream()
                            .filter(f -> f.getKey().equals(feedKey))
                            .findFirst()
                            .orElseThrow(() -> new DataNotFoundException("Feed not found", HttpStatus.NOT_FOUND));

                    return adafruitService.deleteFeed(user.getUsername(), user.getApikey(), feedKey)
                            .onErrorResume(e -> Mono.error(new CustomAuthException("Failed to delete feed on Adafruit: " + e.getMessage(), HttpStatus.BAD_REQUEST)))
                            .then(userProvider.deleteFeedFromGroup(user.getId(), groupKey, feedKey))
                            .then(mqttService.unsubscribeUserFeed(user, feed));
                });
    }

    public Mono<Void> deleteGroup(String email, String groupKey) {
        return userProvider.findByEmail(email)
                .flatMap(user -> {

                    Group group = user.getGroups().stream()
                            .filter(g -> g.getKey().equals(groupKey))
                            .findFirst()
                            .orElseThrow(() -> new DataNotFoundException("Group not found", HttpStatus.NOT_FOUND));

                    List<Feed> feeds = group.getFeeds();

                    return adafruitService.deleteGroup(user.getUsername(), user.getApikey(), groupKey)
                            .onErrorResume(e -> Mono.error(new CustomAuthException("Failed to delete group on Adafruit: " + e.getMessage(), HttpStatus.BAD_REQUEST)))
                            .then(userProvider.deleteGroup(user.getId(), groupKey))
                            .then(mqttService.unsubscribeGroupFeeds(user, feeds));
                });
    }

    public Mono<Group> updateGroup(String email, String currentGroupKey, UpdateGroupRequest request) {
        return userProvider.findByEmail(email)
                .flatMap(user -> userProvider.findGroupByUserId(user.getId(), currentGroupKey)
                        .flatMap(group -> {
                            String formatKey = request.getKey().replace(" ", "-");
                            return adafruitService.updateGroup(user.getUsername(), user.getApikey(), currentGroupKey, request)
                                    .then(Mono.just(user))
                                    .flatMap(u -> {
                                        if (!currentGroupKey.equals(formatKey)) {
                                            return userProvider.updateGroupKey(u, currentGroupKey, formatKey)
                                                    .then(updateSubscriptionInGroup(u, group, currentGroupKey, formatKey))
                                                    .thenReturn(u);
                                        }
                                        return Mono.just(u);
                                    })
                                    .flatMap(u -> userProvider.updateGroupName(u, formatKey, request.getName()))
                                    .flatMap(u -> userProvider.updateGroupDescription(u, formatKey, request.getDescription()))
                                    .then(userProvider.findGroupByUserId(user.getId(), formatKey));
                        }));
    }



    public Mono<Feed> updateFeedForGroup(String email, String groupKey, String oldFullFeedKey, UpdateFeedRequest request) {
        String newFullFeedKey = groupKey + "." + request.getKey();
        return userProvider.findByEmail(email)
                .flatMap(user -> userProvider.updateFeedInGroup(user, groupKey, oldFullFeedKey, request)
                        .flatMap(feed -> adafruitService.updateFeed(user.getUsername(), user.getApikey(), groupKey, oldFullFeedKey, request)
                                .then(Mono.defer(() -> {
                                    if (!oldFullFeedKey.equals(newFullFeedKey)) {
                                        return mqttService.updateFeedSubscription(user, oldFullFeedKey, newFullFeedKey);
                                    }
                                    return Mono.empty();
                                }))
                                .then(userProvider.saveUser(user)) // Save here since updateFeedInGroup doesn’t
                                .thenReturn(feed)))
                .doOnSuccess(feed -> log.info("Updated feed {} to {} for user {}", oldFullFeedKey, newFullFeedKey, email))
                .doOnError(e -> log.error("Error updating feed for {}: {}", email, e.getMessage()));
    }

    private Mono<Void> updateSubscriptionInGroup(User user, Group group, String oldGroupKey, String newGroupKey) {
        return Flux.fromIterable(group.getFeeds())
                .flatMap(feed -> {
                    String[] feedKeyParts = feed.getKey().split("\\.");
                    String feedName = feedKeyParts.length > 1 ? feedKeyParts[1] : feed.getKey();
                    String oldTopic = user.getUsername() + "/feeds/" + oldGroupKey + "." + feedName + "/json";
                    String newTopic = user.getUsername() + "/feeds/" + newGroupKey + "." + feedName + "/json";
                    return mqttSubscriptionManager.unsubscribeFeed(user, oldTopic)
                            .then(mqttSubscriptionManager.updateSubscription(user, newTopic));
                })
                .then();
    }


}