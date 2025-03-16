package QLNKcom.example.QLNK.service.user;

import QLNKcom.example.QLNK.DTO.RegisterRequest;
import QLNKcom.example.QLNK.model.User;
import QLNKcom.example.QLNK.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public Mono<User> findByEmail(String email) {
        System.out.println("🚀 Finding user: " + email);
        return userRepository.findByEmail(email);
    }

    public Mono<User> findById(String userId) {
        System.out.println("🚀 Finding user: " + userId);
        return userRepository.findById(userId);
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
}
