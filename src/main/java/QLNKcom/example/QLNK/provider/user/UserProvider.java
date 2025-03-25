package QLNKcom.example.QLNK.provider.user;

import QLNKcom.example.QLNK.model.User;
import QLNKcom.example.QLNK.model.adafruit.Group;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

public interface UserProvider {
    Mono<User> findById(String userId);
    Mono<User> findByEmail(String email);
    Mono<User> findByUsername(String username);
    Mono<Tuple2<User, Group>> findGroupByKey(String userId, String groupKey);
    Mono<User> saveUser(User user);
    Mono<Void> deleteFeedFromGroup(String userId, String groupKey, String feedKey);
    Mono<Void> deleteGroup(String userId, String groupKey);
    Mono<Void> updateGroupKey(String userId, String oldGroupKey, String newGroupKey);
    Mono<Void> updateGroupDescription(String userId, String groupKey, String newDescription);
    Mono<Void> updateGroupName(String userId, String groupKey, String newName);
}
