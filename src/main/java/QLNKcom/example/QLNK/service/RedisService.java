package QLNKcom.example.QLNK.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RedisService {
    private final ReactiveRedisTemplate<String, String> redisTemplate;

    public Mono<Void> saveRefreshTokenIat(String email, Long iat) {
        return redisTemplate.opsForValue()
                .set("refresh:iat:" + email, String.valueOf(iat), Duration.ofDays(7))
                .then();
    }

    public Mono<Long> getRefreshTokenIat(String email) {
        return redisTemplate.opsForValue()
                .get("refresh:iat:" + email)
                .map(Long::parseLong);
    }

    public Mono<Void> deleteRefreshTokenIat(String email) {
        return redisTemplate.delete("refresh:iat:" + email).then();
    }

}
