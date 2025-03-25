package QLNKcom.example.QLNK.service.user;

import QLNKcom.example.QLNK.DTO.CreateFeedRequest;
import QLNKcom.example.QLNK.DTO.CreateGroupRequest;
import QLNKcom.example.QLNK.DTO.RegisterRequest;
import QLNKcom.example.QLNK.DTO.UpdateGroupRequest;
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
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

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
                .flatMap(user -> userProvider.findGroupByKey(user.getId(), currentGroupKey)
                        .flatMap(tuple -> {
                            User rootUser = tuple.getT1();
                            Group group = tuple.getT2();

                            String formatKey = request.getKey().replace(" ", "-");

                            return adafruitService.updateGroup(rootUser.getUsername(), rootUser.getApikey(), currentGroupKey, request)
                                    .then(Mono.defer(() -> {
                                        Mono<Void> updates = Mono.empty();

                                        if (!currentGroupKey.equals(formatKey)) {
                                            updates = updates.then(userProvider.updateGroupKey(rootUser.getId(), currentGroupKey, formatKey)
                                                    .doOnSuccess(v -> {
                                                        group.getFeeds().forEach(feed -> {
                                                            String[] feedKeyParts = feed.getKey().split("\\.");
                                                            String feedName = feedKeyParts.length > 1 ? feedKeyParts[1] : feed.getKey();
                                                            String oldTopic = user.getUsername() + "/feeds/" + currentGroupKey + "." + feedName + "/json";
                                                            String newTopic = user.getUsername() + "/feeds/" + formatKey + "." + feedName + "/json";
                                                            mqttSubscriptionManager.unsubscribeFeed(rootUser, oldTopic).subscribe();
                                                            mqttSubscriptionManager.updateSubscription(rootUser, newTopic).subscribe();
                                                        });
                                                    }));
                                        }

                                        updates = updates.then(
                                                Mono.when(
                                                        userProvider.updateGroupName(rootUser.getId(), formatKey, request.getName()),
                                                        userProvider.updateGroupDescription(rootUser.getId(), formatKey, request.getDescription())
                                                )
                                        );

                                        return updates.then(userProvider.findGroupByKey(rootUser.getId(), formatKey)
                                                .map(Tuple2::getT2));
                                    }));
                        }));
    }

}