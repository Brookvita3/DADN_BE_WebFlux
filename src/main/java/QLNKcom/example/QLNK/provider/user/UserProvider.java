package QLNKcom.example.QLNK.provider.user;

import QLNKcom.example.QLNK.DTO.UpdateFeedRequest;
import QLNKcom.example.QLNK.model.User;
import QLNKcom.example.QLNK.model.adafruit.Feed;
import QLNKcom.example.QLNK.model.adafruit.Group;
import QLNKcom.example.QLNK.model.data.FeedRule;
import reactor.core.publisher.Mono;

public interface UserProvider {
    Mono<User> findById(String userId);
    Mono<User> findByEmail(String email);
    Mono<Feed> findFeedByKey(String userId, String fullFeedKey);
    Mono<Group> findGroupByUserId(String userId, String groupKey);

    Mono<Feed> updateFeedInGroup(User user, String groupKey, String oldFullFeedKey, UpdateFeedRequest request);
    Mono<User> updateGroupKey(User user, String oldGroupKey, String newGroupKey);
    Mono<User> updateGroupName(User user, String groupKey, String newName);
    Mono<User> updateGroupDescription(User user, String groupKey, String newDescription);

    Mono<User> saveUser(User user);

    Mono<Void> deleteFeedFromGroup(String userId, String groupKey, String feedKey);
    Mono<Void> deleteGroup(String userId, String groupKey);

    Mono<FeedRule> findByEmailAndFullFeedKey(String email, String fullFeedKey);
    Mono<Void> updateFeedRulesEmail(String oldEmail, String newEmail);
}
