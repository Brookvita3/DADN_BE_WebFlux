package QLNKcom.example.QLNK.service.redis;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoOperator;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Service
public class RedisService {
    private final ReactiveRedisTemplate<String, String> redisTemplate;

    public RedisService(@Qualifier("reactiveRedisTemplate") ReactiveRedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

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

    public Mono<String> getValue(String key) {
        return redisTemplate.opsForValue()
                .get(key)
                .doOnSuccess(value -> System.out.println("✅ [REDIS] Get value success for key: " + key));
    }

    public Mono<Void> saveResetPasswordToken(String resetKey, String email) {
        return redisTemplate.opsForValue()
                .set(resetKey, email, Duration.ofMinutes(5))
                .doOnSuccess(success -> System.out.println("✅ [REDIS] Save reset password token success for " + email))
                .then();
    }

    public Mono<String> getResetPasswordToken(String resetKey) {
        return redisTemplate.opsForValue()
                .get(resetKey)
                .doOnSuccess(success -> System.out.println("✅ [REDIS] Get resetKey success"));
    }

    public Mono<Void> deletePassword(String resetKey) {
        return redisTemplate.delete(resetKey)
                .doOnSuccess(success -> System.out.println("✅ [REDIS] Delete reset key"))
                .then();
    }

}
