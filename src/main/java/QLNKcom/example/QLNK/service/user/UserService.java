package QLNKcom.example.QLNK.service.user;

import QLNKcom.example.QLNK.DTO.CreateFeedRequest;
import QLNKcom.example.QLNK.DTO.CreateGroupRequest;
import QLNKcom.example.QLNK.DTO.RegisterRequest;
import QLNKcom.example.QLNK.exception.CustomAuthException;
import QLNKcom.example.QLNK.exception.DataNotFoundException;
import QLNKcom.example.QLNK.model.User;
import QLNKcom.example.QLNK.model.adafruit.Feed;
import QLNKcom.example.QLNK.model.adafruit.Group;
import QLNKcom.example.QLNK.repository.UserRepository;
import QLNKcom.example.QLNK.service.adafruit.AdafruitService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdafruitService adafruitService;

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
                    return saveUser(user).thenReturn(newGroup);
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
                            return saveUser(user).thenReturn(createdFeed);
                        })
                );
    }

    public Mono<User> findByEmail(String email) {
        System.out.println("🚀 Finding user: " + email);
        return userRepository.findByEmail(email)
                .switchIfEmpty(Mono.error(new DataNotFoundException("User not found", HttpStatus.NOT_FOUND)));
    }

    public Mono<User> findById(String userId) {
        System.out.println("🚀 Finding user: " + userId);
        return userRepository.findById(userId)
                .switchIfEmpty(Mono.error(new DataNotFoundException("User not found", HttpStatus.NOT_FOUND)));
    }

    public Mono<User> findByUsername(String username) {
        System.out.println("🚀 Finding user: " + username);
        return userRepository.findByUsername(username)
                .switchIfEmpty(Mono.error(new DataNotFoundException("User not found", HttpStatus.NOT_FOUND)));
    }

    public Mono<User> saveUser(User user) {
        System.out.println("🚀 Saving user: " + user.getEmail());
        return userRepository.save(user);
    }

    public Mono<User> createUser(RegisterRequest request) {
        User newUser = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .apikey(request.getApikey())
                .username(request.getUsername())
                .build();

        return saveUser(newUser);
    }

    public Mono<Group> createGroupByEmail(CreateGroupRequest request, String email) {
        return findByEmail(email)
                .flatMap(user -> adafruitService.createUserGroup(user.getUsername(), user.getApikey(), request)
                        .onErrorResume(e -> Mono.error(new CustomAuthException("Failed to create group on Adafruit: " + e.getMessage(), HttpStatus.BAD_REQUEST)))
                        .flatMap(adafruitGroup -> saveGroupToDatabase(user, adafruitGroup))
                );
    }

    public Mono<Feed> createFeedForGroup(CreateFeedRequest request, String email, String groupKey) {
        return findByEmail(email)
                .flatMap(user -> adafruitService.createFeed(user.getUsername(), user.getApikey(), groupKey, request)
                        .onErrorResume(e -> Mono.error(new CustomAuthException("Failed to create feed on Adafruit: " + e.getMessage(), HttpStatus.BAD_REQUEST)))
                        .flatMap(feed -> saveFeedToDatabase(user, groupKey, feed)));
    }


    public Mono<List<Group>> getAllGroupsByEmail(String email) {
        return findByEmail(email).map(User::getGroups);
    }

}
