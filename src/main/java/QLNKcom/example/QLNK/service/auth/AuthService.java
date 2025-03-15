package QLNKcom.example.QLNK.service.auth;

import QLNKcom.example.QLNK.DTO.LoginRequest;
import QLNKcom.example.QLNK.DTO.RefreshRequest;
import QLNKcom.example.QLNK.DTO.RegisterRequest;
import QLNKcom.example.QLNK.config.jwt.JwtUtils;
import QLNKcom.example.QLNK.exception.AdafruitException;
import QLNKcom.example.QLNK.exception.CustomAuthException;
import QLNKcom.example.QLNK.exception.DataNotFoundException;
import QLNKcom.example.QLNK.model.User;
import QLNKcom.example.QLNK.repository.UserRepository;
import QLNKcom.example.QLNK.response.auth.AuthResponse;
import QLNKcom.example.QLNK.service.adafruit.AdafruitService;
import QLNKcom.example.QLNK.service.redis.RedisService;
import QLNKcom.example.QLNK.service.user.CustomReactiveUserDetailsService;
import QLNKcom.example.QLNK.service.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserService userService;
    private final AdafruitService adafruitService;
    private final CustomReactiveUserDetailsService customReactiveUserDetailsService;
    private final RedisService redisService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final UserRepository userRepository;

    public Mono<AuthResponse> authenticate(LoginRequest request) {
        return customReactiveUserDetailsService.findByUsername(request.getEmail())
                .filter(userDetails -> passwordEncoder.matches(request.getPassword(), userDetails.getPassword()))
                .switchIfEmpty(Mono.error(new BadCredentialsException("Invalid credentials")))
                .flatMap(userDetails -> userRepository.findByEmail(request.getEmail())
                        .switchIfEmpty(Mono.error(new DataNotFoundException("User not found in database", HttpStatus.NOT_FOUND)))
                )
                .flatMap(this::generateTokensAndCache)  // ✅ Trả về Mono<AuthResponse>
                .flatMap(response -> userRepository.findByEmail(request.getEmail())
                        .flatMap(user -> fetchAndStoreFeeds(response, user))
                .doOnError(error -> System.err.println("❌ Error in login flow: " + error.getMessage()))
                );
    }

    public Mono<AuthResponse> register(RegisterRequest request) {
        return userService.findByEmail(request.getEmail())
                .flatMap(existingUser -> Mono.error(new CustomAuthException("Email already in use", HttpStatus.BAD_REQUEST)))
                .switchIfEmpty(Mono.defer(() -> userService.createUser(request)
                        .flatMap(this::generateTokensAndCache)
                ))
                .cast(AuthResponse.class)
                .doOnError(error -> System.err.println("❌ Error in register flow: " + error.getMessage()));
    }


    public Mono<Void> logout(ServerHttpRequest request) {
        return jwtUtils.extractToken(request)
                .flatMap(jwtUtils::extractAccessEmail)
                .flatMap(redisService::deleteRefreshTokenIat)
                .doOnError(error -> System.err.println("❌ Error in logout flow: " + error.getMessage()))
                .then();
    }

    public Mono<AuthResponse> refresh(RefreshRequest request) {
        String refreshToken = request.getRefreshToken();

        return jwtUtils.extractRefreshClaims(refreshToken)
                .flatMap(claims -> {
                    String email = claims.getSubject();
                    long tokenIatSecond = claims.getIssuedAt().getTime() / 1000;

                    return redisService.getRefreshTokenIat(email)
                            .switchIfEmpty(Mono.error(new CustomAuthException("Invalid or revoked refresh token", HttpStatus.UNAUTHORIZED)))
                            .flatMap(savedIat -> {
                                if (!savedIat.equals(tokenIatSecond)) {
                                    return Mono.error(new CustomAuthException("Invalid or revoked refresh token", HttpStatus.UNAUTHORIZED));
                                }
                                return jwtUtils.generateAccessToken(email)
                                        .map(accessToken -> new AuthResponse(accessToken, refreshToken));
                            });
                });
    }

    private Mono<AuthResponse> generateTokensAndCache(User user) {
        return jwtUtils.generateAccessToken(user.getEmail())
                .zipWhen(accessToken -> jwtUtils.generateRefreshToken(user.getEmail()))
                .flatMap(tokens -> jwtUtils.extractRefreshIat(tokens.getT2())
                        .flatMap(iatSeconds -> redisService.saveRefreshTokenIat(user.getEmail(), iatSeconds.getTime() / 1000))
                        .thenReturn(new AuthResponse(tokens.getT1(), tokens.getT2()))
                );
    }

    private Mono<AuthResponse> fetchAndStoreFeeds(AuthResponse response, User user) {
        return adafruitService.getUserFeeds(user.getUsername(), user.getApikey())
                .flatMap(feeds -> {
                    user.setFeeds(feeds);
                    return userRepository.save(user);
                })
                .thenReturn(response)
                .onErrorMap(error -> new AdafruitException("Exception in connect to Adafruit", HttpStatus.BAD_REQUEST));
    }

}
