package QLNKcom.example.QLNK.service.user;

import QLNKcom.example.QLNK.DTO.CreateGroupRequest;
import QLNKcom.example.QLNK.DTO.RegisterRequest;
import QLNKcom.example.QLNK.exception.CustomAuthException;
import QLNKcom.example.QLNK.exception.DataNotFoundException;
import QLNKcom.example.QLNK.model.User;
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
                .flatMap(user -> {

                    boolean groupExists = user.getGroups().stream()
                            .anyMatch(group -> group.getName().equals(request.getName()));

                    if (groupExists) {
                        return Mono.error(new CustomAuthException("Group name already exists", HttpStatus.BAD_REQUEST));
                    }

                    Group group = Group.builder()
                            .name(request.getName())
                            .description(request.getDescription())
                            .feeds(request.getFeeds())
                            .build();

                    user.getGroups().add(group);
                    return adafruitService.createUserGroup(user.getUsername(), user.getApikey(), request)
                            .flatMap(adafruitGroup -> {
                                user.getGroups().add(group);
                                return saveUser(user).thenReturn(group);
                            });
                });
    }

    public Mono<List<Group>> getAllGroupsByEmail(String email) {
        return findByEmail(email).map(User::getGroups);
    }

}
