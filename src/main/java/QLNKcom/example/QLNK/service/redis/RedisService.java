package QLNKcom.example.QLNK.service.redis;

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
                .doOnSuccess(success -> System.out.println("✅ [REDIS] Save success for " + email))
                .then();
    }

    public Mono<Long> getRefreshTokenIat(String email) {
        return redisTemplate.opsForValue()
                .get("refresh:iat:" + email)
                .map(Long::parseLong)
                .doOnSuccess(success -> System.out.println("✅ [REDIS] Get success for " + email));
    }

    public Mono<Void> deleteRefreshTokenIat(String email) {
        return redisTemplate.delete("refresh:iat:" + email)
                .doOnSuccess(success -> System.out.println("✅ [REDIS] Delete success for " + email))
                .then();
    }

}
