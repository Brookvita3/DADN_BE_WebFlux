package QLNKcom.example.QLNK.repository;

import QLNKcom.example.QLNK.model.data.DeviceData;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

public interface DeviceDataRepository extends ReactiveMongoRepository<DeviceData, String> {
}
