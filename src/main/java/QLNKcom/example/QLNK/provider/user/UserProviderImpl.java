package QLNKcom.example.QLNK.provider.user;

import QLNKcom.example.QLNK.exception.DataNotFoundException;
import QLNKcom.example.QLNK.model.User;
import QLNKcom.example.QLNK.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class UserProviderImpl implements UserProvider {

    private final UserRepository userRepository;

    @Override
    public Mono<User> findById(String userId) {
        System.out.println("🚀 Finding user: " + userId);
        return userRepository.findById(userId)
                .switchIfEmpty(Mono.error(new DataNotFoundException("User not found", HttpStatus.NOT_FOUND)));
    }

    @Override
    public Mono<User> findByEmail(String email) {
        System.out.println("🚀 Finding user: " + email);
        return userRepository.findByEmail(email)
                .switchIfEmpty(Mono.error(new DataNotFoundException("User not found", HttpStatus.NOT_FOUND)));
    }

    @Override
    public Mono<User> findByUsername(String username) {
        System.out.println("🚀 Finding user: " + username);
        return userRepository.findByUsername(username)
                .switchIfEmpty(Mono.error(new DataNotFoundException("User not found", HttpStatus.NOT_FOUND)));
    }

    @Override
    public Mono<User> saveUser(User user) {
        System.out.println("🚀 Saving user: " + user.getEmail());
        return userRepository.save(user);
    }

    @Override
    public Mono<Void> deleteFeedFromGroup(String userId, String groupKey, String feedKey) {
        return userRepository.deleteFeedFromGroup(userId, groupKey, feedKey);
    }
}
