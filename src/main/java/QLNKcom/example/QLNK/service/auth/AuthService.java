package QLNKcom.example.QLNK.service.auth;

import QLNKcom.example.QLNK.DTO.LoginRequest;
import QLNKcom.example.QLNK.DTO.RefreshRequest;
import QLNKcom.example.QLNK.DTO.RegisterRequest;
import QLNKcom.example.QLNK.config.jwt.JwtUtils;
import QLNKcom.example.QLNK.exception.CustomAuthException;
import QLNKcom.example.QLNK.response.auth.AuthResponse;
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
    private final CustomReactiveUserDetailsService customReactiveUserDetailsService;
    private final RedisService redisService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;

    public Mono<AuthResponse> authenticate(LoginRequest request) {
        return customReactiveUserDetailsService.findByUsername(request.getEmail())
                .flatMap(user -> {
                    if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
                        return Mono.error(new BadCredentialsException("Invalid credentials"));
                    }

                    return jwtUtils.generateAccessToken(user.getUsername())
                            .zipWith(jwtUtils.generateRefreshToken(user.getUsername()))
                            .flatMap(tokens -> jwtUtils.extractRefreshIat(tokens.getT2())
                                    .flatMap(iatSeconds -> redisService.saveRefreshTokenIat(user.getUsername(), iatSeconds.getTime() / 1000))
                                    .thenReturn(new AuthResponse(tokens.getT1(), tokens.getT2()))
                            )
                            .doOnError(error -> System.err.println("❌ Error in login flow: " + error.getMessage()));
                });
    }

    public Mono<Void> register(RegisterRequest request) {
        return userService.findByEmail(request.getEmail())
                .flatMap(existingUser -> Mono.error(new CustomAuthException("Email already in use", HttpStatus.BAD_REQUEST)))
                .switchIfEmpty(Mono.defer(() -> userService.createUser(request)
                        .flatMap(user -> jwtUtils.generateAccessToken(user.getEmail())
                                .zipWith(jwtUtils.generateRefreshToken(user.getEmail()))
                                .flatMap(tokens -> jwtUtils.extractRefreshIat(tokens.getT2())
                                        .flatMap(iatSeconds -> redisService.saveRefreshTokenIat(user.getEmail(), iatSeconds.getTime() / 1000))
                                )
                        )
                ))
                .then()
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
                    long tokenIat = claims.getIssuedAt().getTime() / 1000;

                    return redisService.getRefreshTokenIat(email)
                            .flatMap(savedIat -> {
                                if (!savedIat.equals(tokenIat)) {
                                    return Mono.error(new CustomAuthException("Invalid or revoked refresh token", HttpStatus.UNAUTHORIZED));
                                }
                                return jwtUtils.generateAccessToken(email)
                                        .map(accessToken -> new AuthResponse(accessToken, refreshToken));
                            });
                });
    }

}
