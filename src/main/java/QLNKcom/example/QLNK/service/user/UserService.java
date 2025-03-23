package QLNKcom.example.QLNK.service.user;

import QLNKcom.example.QLNK.DTO.CreateFeedRequest;
import QLNKcom.example.QLNK.DTO.CreateGroupRequest;
import QLNKcom.example.QLNK.DTO.RegisterRequest;
import QLNKcom.example.QLNK.exception.CustomAuthException;
import QLNKcom.example.QLNK.model.User;
import QLNKcom.example.QLNK.model.adafruit.Feed;
import QLNKcom.example.QLNK.model.adafruit.Group;
import QLNKcom.example.QLNK.provider.user.UserProvider;
import QLNKcom.example.QLNK.service.adafruit.AdafruitService;
import QLNKcom.example.QLNK.service.mqtt.MqttService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserProvider userProvider;
    private final PasswordEncoder passwordEncoder;
    private final AdafruitService adafruitService;
    private final MqttService mqttService;

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
                            .orElseThrow(() -> new CustomAuthException("Group not found", HttpStatus.NOT_FOUND));

                    Feed feed = group.getFeeds().stream()
                            .filter(f -> f.getKey().equals(feedKey))
                            .findFirst()
                            .orElseThrow(() -> new CustomAuthException("Feed not found", HttpStatus.NOT_FOUND));

                    return adafruitService.deleteFeed(user.getUsername(), user.getApikey(), feedKey)
                            .onErrorResume(e -> Mono.error(new CustomAuthException("Failed to delete feed on Adafruit: " + e.getMessage(), HttpStatus.BAD_REQUEST)))
                            .then(userProvider.deleteFeedFromGroup(user.getId(), groupKey, feedKey))
                            .then(mqttService.unsubscribeUserFeed(user, feed));
                });
    }

    public Mono<Void> deleteGroup(String email, String groupKey) {
        return userProvider.findByEmail(email)
                .flatMap(user -> {
                    // Check if the group exists
                    Group group = user.getGroups().stream()
                            .filter(g -> g.getKey().equals(groupKey))
                            .findFirst()
                            .orElseThrow(() -> new CustomAuthException("Group not found", HttpStatus.NOT_FOUND));

                    List<Feed> feeds = group.getFeeds();

                    // Delete group from Adafruit, then delete group from MongoDB, then unsubscribe from MQTT
                    return adafruitService.deleteGroup(user.getUsername(), user.getApikey(), groupKey)
                            .onErrorResume(e -> Mono.error(new CustomAuthException("Failed to delete group on Adafruit: " + e.getMessage(), HttpStatus.BAD_REQUEST)))
                            .then(userProvider.deleteGroup(user.getId(), groupKey))
                            .then(mqttService.unsubscribeGroupFeeds(user, feeds));
                });
    }


}
