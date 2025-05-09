package QLNKcom.example.QLNK.repository;

import QLNKcom.example.QLNK.model.data.FeedRule;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface FeedRuleRepository extends ReactiveMongoRepository<FeedRule, String> {
    Mono<FeedRule> findByEmailAndInputFeed(String email, String inputFullFeedKey);
    Flux<FeedRule> findByEmail(String email);
    Mono<FeedRule> findByInputFeedAndOutputFeedAboveAndOutputFeedBelow(
            String inputFeed, String outputFeedAbove, String outputFeedBelow);
    Mono<Void> deleteByEmailAndInputFeedOrOutputFeedAboveOrOutputFeedBelow(
            String email, String inputFeed, String outputFeedAbove, String outputFeedBelow);
    @Query("{ 'email': ?0, $or: [ { 'inputFeed': ?1 }, { 'outputFeedAbove': ?1 }, { 'outputFeedBelow': ?1 } ] }")
    Flux<FeedRule> findByEmailAndFeedKey(String email, String feedKey);
}
