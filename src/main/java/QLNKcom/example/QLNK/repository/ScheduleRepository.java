package QLNKcom.example.QLNK.repository;

import QLNKcom.example.QLNK.model.data.Schedule;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

public interface ScheduleRepository extends ReactiveMongoRepository<Schedule, String> {
}
