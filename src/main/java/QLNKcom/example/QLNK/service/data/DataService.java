package QLNKcom.example.QLNK.service.data;

import QLNKcom.example.QLNK.repository.DeviceDataRepository;
import QLNKcom.example.QLNK.repository.SensorDataRepository;
import QLNKcom.example.QLNK.service.websocket.WebSocketSessionManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class DataService {

    private final DeviceDataRepository deviceDataRepository;
    private final SensorDataRepository sensorDataRepository;
    private final WebSocketSessionManager webSocketSessionManager;
    private final ObjectMapper objectMapper;

}
