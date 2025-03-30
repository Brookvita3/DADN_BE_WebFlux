package QLNKcom.example.QLNK.provider.user;

import QLNKcom.example.QLNK.DTO.feed.UpdateFeedRequest;
import QLNKcom.example.QLNK.exception.DataNotFoundException;
import QLNKcom.example.QLNK.model.User;
import QLNKcom.example.QLNK.model.adafruit.Feed;
import QLNKcom.example.QLNK.model.adafruit.Group;
import QLNKcom.example.QLNK.model.data.FeedRule;
import QLNKcom.example.QLNK.repository.FeedRuleRepository;
import QLNKcom.example.QLNK.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserProviderImpl implements UserProvider {

    private final UserRepository userRepository;
    private final FeedRuleRepository feedRuleRepository;

    @Override
    public Mono<User> findById(String userId) {
        log.info("🚀 Finding user with userId: {}", userId);
        return userRepository.findById(userId)
                .switchIfEmpty(Mono.error(new DataNotFoundException("User not found", HttpStatus.NOT_FOUND)));
    }

    @Override
    public Mono<User> findByEmail(String email) {
        log.info("🚀 Finding user with email: {}", email);
        return userRepository.findByEmail(email)
                .switchIfEmpty(Mono.error(new DataNotFoundException("User not found", HttpStatus.NOT_FOUND)));
    }

    @Override
    public Mono<FeedRule> findByEmailAndFullFeedKey(String email, String inputFullFeedKey) {
        log.info("🚀 Finding feed rule {} of email {}", inputFullFeedKey, email);
        return feedRuleRepository.findByEmailAndInputFeed(email, inputFullFeedKey)
                .switchIfEmpty(Mono.error(new DataNotFoundException("Feed rule not found for email: " + email + " and inputFullFeedKey: " + inputFullFeedKey, HttpStatus.NOT_FOUND)));
    }

    @Override
    public Mono<User> saveUser(User user) {
        log.info("🚀 Saving user with email {} ", user.getEmail());
        return userRepository.save(user);
    }

    @Override
    public Mono<Void> deleteFeedFromGroup(String userId, String groupKey, String feedKey) {
        return userRepository.deleteFeedFromGroup(userId, groupKey, feedKey);
    }

    @Override
    public Mono<Void> deleteGroup(String userId, String groupKey) {
        return userRepository.deleteGroup(userId, groupKey);
    }

    @Override
    public Mono<Feed> findFeedByKey(String userId, String fullFeedKey) {
        return findById(userId)
                .flatMap(user -> Mono.justOrEmpty(user.getGroups().stream()
                        .flatMap(group -> group.getFeeds().stream())
                        .filter(feed -> feed.getKey().equals(fullFeedKey))
                        .findFirst()))
                .switchIfEmpty(Mono.error(new DataNotFoundException("Feed not found", HttpStatus.NOT_FOUND)))
                .doOnSuccess(feed -> log.debug("Found feed {} for user {}", fullFeedKey, userId))
                .doOnError(e -> log.error("Error finding feed {} for user {}: {}", fullFeedKey, userId, e.getMessage()));
    }

    @Override
    public Mono<Group> findGroupByUserId(String userId, String groupKey) {
        return findById(userId)
                .flatMap(user -> Mono.justOrEmpty(user.getGroups().stream()
                                .filter(g -> g.getKey().equals(groupKey))
                                .findFirst())
                        .switchIfEmpty(Mono.error(new DataNotFoundException("Group not found: " + groupKey, HttpStatus.NOT_FOUND))));
    }

    @Override
    public Mono<Feed> updateFeedInGroup(User user, String groupKey, String oldFullFeedKey, UpdateFeedRequest request) {
        return Mono.justOrEmpty(user.getGroups().stream()
                        .filter(g -> g.getKey().equals(groupKey))
                        .findFirst())
                .switchIfEmpty(Mono.error(new DataNotFoundException("Group not found: " + groupKey, HttpStatus.NOT_FOUND)))
                .flatMap(group -> Mono.justOrEmpty(group.getFeeds().stream()
                                .filter(f -> f.getKey().equals(oldFullFeedKey))
                                .findFirst())
                        .switchIfEmpty(Mono.error(new DataNotFoundException("Feed not found: " + oldFullFeedKey, HttpStatus.NOT_FOUND)))
                        .map(feed -> {
                            String newFullFeedKey = groupKey + "." + request.getKey();
                            feed.setName(request.getName());
                            feed.setDescription(request.getDescription());
                            feed.setKey(newFullFeedKey);
                            return feed;
                        }));
    }


    @Override
    public Mono<User> updateGroupName(User user, String groupKey, String newName) {
        return Mono.justOrEmpty(user.getGroups().stream()
                        .filter(g -> g.getKey().equals(groupKey))
                        .findFirst())
                .switchIfEmpty(Mono.error(new DataNotFoundException("Group not found: " + groupKey, HttpStatus.NOT_FOUND)))
                .flatMap(group -> {
                    if (newName == null || newName.equals(group.getName())) {
                        return Mono.empty();
                    }
                    group.setName(newName);
                    return userRepository.save(user)
                            .doOnSuccess(savedUser -> log.info("Saved user with updated group name: {} -> {}", group.getName(), newName));
                })
                .doOnError(e -> log.error("Error updating group name: {}", e.getMessage()));
    }

    @Override
    public Mono<User> updateGroupDescription(User user, String groupKey, String newDescription) {
        return Mono.justOrEmpty(user.getGroups().stream()
                        .filter(g -> g.getKey().equals(groupKey))
                        .findFirst())
                .switchIfEmpty(Mono.error(new DataNotFoundException("Group not found: " + groupKey, HttpStatus.NOT_FOUND)))
                .flatMap(group -> {
                    if (newDescription == null || newDescription.equals(group.getDescription())) {
                        return Mono.empty();
                    }
                    group.setDescription(newDescription);
                    return userRepository.save(user)
                            .doOnSuccess(savedUser -> log.info("Saved user with updated group description: {} -> {}", group.getDescription(), newDescription));
                })
                .doOnError(e -> log.error("Error updating group description: {}", e.getMessage()));
    }

    @Override
    public Mono<User> updateGroupKey(User user, String oldGroupKey, String newGroupKey) {
        return Mono.justOrEmpty(user.getGroups().stream()
                        .filter(g -> g.getKey().equals(oldGroupKey))
                        .findFirst())
                .switchIfEmpty(Mono.error(new DataNotFoundException("Group not found: " + oldGroupKey, HttpStatus.NOT_FOUND)))
                .flatMap(group -> {
                    if (oldGroupKey.equals(newGroupKey)) {
                        return Mono.empty();
                    }
                    group.setKey(newGroupKey);
                    group.getFeeds().forEach(feed -> {
                        String oldFeedKey = feed.getKey();
                        String newFeedKey = oldFeedKey.replaceFirst(
                                Pattern.quote(oldGroupKey + "."),
                                newGroupKey + "."
                        );
                        feed.setKey(newFeedKey);
                    });
                    return userRepository.save(user)
                            .doOnSuccess(savedUser -> log.info("User after update successfully: {}", savedUser));
                });
    }

    @Override
    public Mono<Void> updateFeedRulesEmail(String oldEmail, String newEmail) {
        return feedRuleRepository.findByEmail(oldEmail)
                .flatMap(feedRule -> {
                    feedRule.setEmail(newEmail);
                    return feedRuleRepository.save(feedRule);
                })
                .then();
    }
}
