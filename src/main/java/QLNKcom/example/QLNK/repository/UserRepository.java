package QLNKcom.example.QLNK.repository;

import QLNKcom.example.QLNK.model.User;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.data.mongodb.repository.Update;
import reactor.core.publisher.Mono;

public interface UserRepository extends ReactiveMongoRepository<User, String> {

    Mono<User> findByEmail(String email);

    Mono<User> findByUsername(String username);

    @Query("{ '_id': ?0, 'groups.key': ?1 }")
    @Update("{ '$pull': { 'groups.$.feeds': { 'key': ?2 } } }")
    Mono<Void> deleteFeedFromGroup(String userId, String groupKey, String feedKey);

    @Query("{ '_id': ?0 }")
    @Update("{ '$pull': { 'groups': { 'key': ?1 } } }")
    Mono<Void> deleteGroup(String userId, String groupKey);

}
