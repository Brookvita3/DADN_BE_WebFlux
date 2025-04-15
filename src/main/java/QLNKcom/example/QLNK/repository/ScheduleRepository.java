package QLNKcom.example.QLNK.repository;

import QLNKcom.example.QLNK.model.data.Schedule;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ScheduleRepository extends ReactiveMongoRepository<Schedule, String> {
    Flux<Schedule> findByUserIdAndFullFeedKey(String userId, String fullFeedKey);
    Flux<Schedule> findByUserId(String userId);
    Mono<Void> deleteByUserIdAndFullFeedKey(String userId, String fullFeedKey);
}
