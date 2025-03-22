package QLNKcom.example.QLNK.config.redis;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@Configuration
public class RedisConfig {

    @Value("${spring.redis.host}")
    private String redisHost;

    @Value("${spring.redis.port}")
    private int redisPort;

    @Value("${spring.data.redis.timeout}")
    private long redisTimeout;

    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration();
        redisConfig.setHostName(redisHost);
        redisConfig.setPort(redisPort);

        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofMillis(redisTimeout))
                .build();

        return new LettuceConnectionFactory(redisConfig, clientConfig);
    }

    @Bean
    public ReactiveRedisTemplate<String, String> reactiveRedisTemplate(LettuceConnectionFactory connectionFactory) {
        RedisSerializationContext<String, String> serializationContext = RedisSerializationContext
                .<String, String>newSerializationContext(new StringRedisSerializer())
                .build();
        return new ReactiveRedisTemplate<>(connectionFactory, serializationContext);
    }
}
