package QLNKcom.example.QLNK.provider.user;

import QLNKcom.example.QLNK.model.User;
import reactor.core.publisher.Mono;

public interface UserProvider {
    Mono<User> findById(String userId);
    Mono<User> findByEmail(String email);
    Mono<User> findByUsername(String username);
    Mono<User> saveUser(User user);
    Mono<Void> deleteFeedFromGroup(String userId, String groupKey, String feedKey);
    Mono<Void> deleteGroup(String userId, String groupKey);
}
