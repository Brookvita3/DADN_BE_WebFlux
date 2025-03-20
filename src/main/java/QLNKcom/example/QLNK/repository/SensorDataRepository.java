package QLNKcom.example.QLNK.repository;

import QLNKcom.example.QLNK.model.data.SensorData;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

public interface SensorDataRepository extends ReactiveMongoRepository<SensorData, String> {
}
