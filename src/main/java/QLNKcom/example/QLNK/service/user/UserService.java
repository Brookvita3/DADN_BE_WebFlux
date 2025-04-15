package QLNKcom.example.QLNK.service.user;

import QLNKcom.example.QLNK.DTO.feed.CreateFeedRequest;
import QLNKcom.example.QLNK.DTO.feed.UpdateFeedRequest;
import QLNKcom.example.QLNK.DTO.feed.UpdateGroupRequest;
import QLNKcom.example.QLNK.DTO.group.CreateGroupRequest;
import QLNKcom.example.QLNK.DTO.user.CreateFeedRuleRequest;
import QLNKcom.example.QLNK.DTO.user.RegisterRequest;
import QLNKcom.example.QLNK.DTO.user.UpdateFeedRuleRequest;
import QLNKcom.example.QLNK.DTO.user.UpdateInfoRequest;
import QLNKcom.example.QLNK.exception.CustomAuthException;
import QLNKcom.example.QLNK.exception.DataDuplicateException;
import QLNKcom.example.QLNK.exception.DataNotFoundException;
import QLNKcom.example.QLNK.exception.InvalidPasswordException;
import QLNKcom.example.QLNK.model.User;
import QLNKcom.example.QLNK.model.adafruit.Feed;
import QLNKcom.example.QLNK.model.adafruit.Group;
import QLNKcom.example.QLNK.model.data.FeedRule;
import QLNKcom.example.QLNK.provider.user.UserProvider;
import QLNKcom.example.QLNK.repository.FeedRuleRepository;
import QLNKcom.example.QLNK.service.adafruit.AdafruitService;
import QLNKcom.example.QLNK.service.mqtt.MqttService;
import QLNKcom.example.QLNK.service.mqtt.MqttSubscriptionManager;
import QLNKcom.example.QLNK.service.scheduler.ScheduleService;
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
    private final FeedRuleRepository feedRuleRepository;
    private final ScheduleService scheduleService;

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

    public Mono<FeedRule> createFeedRule(String email, CreateFeedRuleRequest request) {
        return userProvider.findByEmail(email)
                .switchIfEmpty(Mono.error(new RuntimeException("User not found")))
                .flatMap(user -> feedRuleRepository.findByInputFeedAndOutputFeedAboveAndOutputFeedBelow(request.getInputFeed(), request.getOutputFeedAbove(),request.getOutputFeedBelow())
                        .hasElement()
                        .flatMap(hasDuplicate -> {
                            if (hasDuplicate) {
                                return Mono.error(new DataDuplicateException(
                                        "FeedRule already exists for inputFeed: " +
                                                request.getInputFeed() +
                                                ", outputFeedAbove: " +
                                                request.getOutputFeedAbove() +
                                                ", outputFeedBelow: " +
                                                request.getOutputFeedBelow(),
                                        HttpStatus.BAD_REQUEST));
                            }

                            FeedRule newFeedRule = FeedRule.builder()
                                    .groupKey(request.getInputFeed().split("\\.")[0])
                                    .email(email)
                                    .inputFeed(request.getInputFeed())
                                    .floor(request.getFloor())
                                    .ceiling(request.getCeiling())
                                    .aboveValue(request.getAboveValue())
                                    .belowValue(request.getBelowValue())
                                    .outputFeedAbove(request.getOutputFeedAbove())
                                    .outputFeedBelow(request.getOutputFeedBelow())
                                    .build();
                            return feedRuleRepository.save(newFeedRule);
                        }));
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

    public Mono<Void> deleteFeed(String email, String groupKey, String fullFeedKey) {
        return userProvider.findByEmail(email)
                .flatMap(user -> {

                    Group group = user.getGroups().stream()
                            .filter(g -> g.getKey().equals(groupKey))
                            .findFirst()
                            .orElseThrow(() -> new DataNotFoundException("Group not found", HttpStatus.NOT_FOUND));

                    Feed feed = group.getFeeds().stream()
                            .filter(f -> f.getKey().equals(fullFeedKey))
                            .findFirst()
                            .orElseThrow(() -> new DataNotFoundException("Feed not found", HttpStatus.NOT_FOUND));

                    return adafruitService.deleteFeed(user.getUsername(), user.getApikey(), fullFeedKey)
                            .onErrorResume(e -> Mono.error(new CustomAuthException("Failed to delete feed on Adafruit: " + e.getMessage(), HttpStatus.BAD_REQUEST)))
                            .then(userProvider.deleteFeedFromGroup(user.getId(), groupKey, fullFeedKey))
                            .then(mqttService.unsubscribeUserFeed(user, feed))
                            .then(feedRuleRepository.deleteByEmailAndInputFeedOrOutputFeedAboveOrOutputFeedBelow(
                                            email, fullFeedKey, fullFeedKey, fullFeedKey)
                                    .onErrorResume(e -> Mono.error(new RuntimeException("Failed to delete feed rules: " + e.getMessage()))))
                            .then(scheduleService.deleteSchedulesByUserIdAndFullFeedKey(user.getId(), fullFeedKey)
                            .onErrorResume(e -> Mono.error(new RuntimeException("Failed to delete schedules: " + e.getMessage()))));
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
                                    Mono<Void> mqttUpdate = !oldFullFeedKey.equals(newFullFeedKey)
                                            ? mqttService.updateFeedSubscription(user, oldFullFeedKey, newFullFeedKey)
                                            : Mono.empty();
                                    return mqttUpdate
                                            .then(updateFeedRulesForFeed(email, oldFullFeedKey, newFullFeedKey)) // Update feed rules
                                            .then(scheduleService.updateSchedulesForFeed(user.getId(), oldFullFeedKey, newFullFeedKey)); // Update schedules
                                }))
                                .then(userProvider.saveUser(user)) // Save user after all updates
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

    public Mono<User> getInfo(String email) {
        return userProvider.findByEmail(email);
    }

    public Mono<User> updateInfo(String email, UpdateInfoRequest request) {
        return userProvider.findByEmail(email)
                .flatMap(user -> {
                    String oldEmail = user.getEmail();

                    if (request.getEmail() != null) {
                        user.setEmail(request.getEmail());
                    }

                    if (request.getApikey() != null) {
                        user.setApikey(request.getApikey());
                    }

                    if (request.getOldPassword() != null && request.getNewPassword() != null) {
                        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
                            return Mono.error(new InvalidPasswordException("Password is invalid", HttpStatus.BAD_REQUEST));
                        }
                        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
                    } else if (request.getOldPassword() != null || request.getNewPassword() != null) {
                        return Mono.error(new IllegalArgumentException("Both oldPassword and password must be provided to update password"));
                    }

                    return userProvider.saveUser(user).flatMap(
                            updatedUser -> {
                                if (!oldEmail.equals(request.getEmail())) {
                                    return userProvider.updateFeedRulesEmail(oldEmail, request.getEmail())
                                            .thenReturn(updatedUser);
                                }
                                return Mono.just(updatedUser);
                            }
                    );
                });
    }

    public Mono<FeedRule> updateFeedRule(String email, String fullFeedKey, UpdateFeedRuleRequest request) {
        return userProvider.findByEmailAndFullFeedKey(email, fullFeedKey)
                .switchIfEmpty(Mono.error(new RuntimeException("Feed rule not found")))
                .flatMap(feedRule -> {
                    feedRule.setInputFeed(request.getInputFeed());
                    feedRule.setCeiling(request.getCeiling());
                    feedRule.setFloor(request.getFloor());
                    feedRule.setOutputFeedAbove(request.getOutputFeedAbove());
                    feedRule.setOutputFeedBelow(request.getOutputFeedBelow());
                    feedRule.setAboveValue(request.getAboveValue());
                    feedRule.setBelowValue(request.getBelowValue());

                    return feedRuleRepository.findByInputFeedAndOutputFeedAboveAndOutputFeedBelow(
                                    feedRule.getInputFeed(),
                                    feedRule.getOutputFeedAbove(),
                                    feedRule.getOutputFeedBelow())
                            .hasElement()
                            .flatMap(hasDuplicate -> {
                                if (hasDuplicate) {
                                    return Mono.error(new DataDuplicateException(
                                            "FeedRule already exists for inputFeed: " +
                                                    feedRule.getInputFeed() +
                                                    ", outputFeedAbove: " +
                                                    feedRule.getOutputFeedAbove() +
                                                    ", outputFeedBelow: " +
                                                    feedRule.getOutputFeedBelow(),
                                            HttpStatus.BAD_REQUEST));
                                }
                                return feedRuleRepository.save(feedRule);
                            });
                });
    }

    public Flux<FeedRule> getFeedRules(String email, String feedName) {
        return feedRuleRepository.findByEmail(email)
                .filter(feedRule -> feedName == null || feedName.isBlank() || feedRule.getInputFeed().equals(feedName));
    }

    private Mono<Void> updateFeedRulesForFeed(String email, String oldFullFeedKey, String newFullFeedKey) {
        return feedRuleRepository.findByEmailAndInputFeed(email, oldFullFeedKey)
                .flatMap(rule -> {
                    boolean updated = false;
                    if (oldFullFeedKey.equals(rule.getInputFeed())) {
                        rule.setInputFeed(newFullFeedKey);
                        updated = true;
                    }
                    if (oldFullFeedKey.equals(rule.getOutputFeedAbove())) {
                        rule.setOutputFeedAbove(newFullFeedKey);
                        updated = true;
                    }
                    if (oldFullFeedKey.equals(rule.getOutputFeedBelow())) {
                        rule.setOutputFeedBelow(newFullFeedKey);
                        updated = true;
                    }
                    return updated ? feedRuleRepository.save(rule).then(Mono.empty()) : Mono.empty();
                })
                .then();
    }

    public Mono<Long> deleteFeedRule(String email, String fullFeedKey) {
        return feedRuleRepository.findByEmailAndFeedKey(email, fullFeedKey)
                .flatMap(rule -> feedRuleRepository.delete(rule).thenReturn(1L))
                .count()
                .doOnSuccess(count -> log.info("Deleted {} feed rules for email {} and feed {}", count, email, fullFeedKey))
                .doOnError(e -> log.error("Error deleting feed rules for email {} and feed {}: {}", email, fullFeedKey, e.getMessage()));
    }

}