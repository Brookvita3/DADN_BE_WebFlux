package QLNKcom.example.QLNK.repository;

import QLNKcom.example.QLNK.model.data.FeedRule;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface FeedRuleRepository extends ReactiveMongoRepository<FeedRule, String> {
    Mono<FeedRule> findByEmailAndInputFeed(String email, String inputFullFeedKey);
    Flux<FeedRule> findByEmail(String email);
}
