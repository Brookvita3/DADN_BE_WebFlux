package QLNKcom.example.QLNK.service.auth;

import QLNKcom.example.QLNK.DTO.user.LoginRequest;
import QLNKcom.example.QLNK.DTO.user.RefreshRequest;
import QLNKcom.example.QLNK.DTO.user.RegisterRequest;
import QLNKcom.example.QLNK.config.jwt.JwtUtils;
import QLNKcom.example.QLNK.exception.AdafruitException;
import QLNKcom.example.QLNK.exception.CustomAuthException;
import QLNKcom.example.QLNK.exception.DataNotFoundException;
import QLNKcom.example.QLNK.model.User;
import QLNKcom.example.QLNK.response.auth.AuthResponse;
import QLNKcom.example.QLNK.service.adafruit.AdafruitService;
import QLNKcom.example.QLNK.service.email.EmailService;
import QLNKcom.example.QLNK.service.redis.RedisService;
import QLNKcom.example.QLNK.service.user.CustomReactiveUserDetailsService;
import QLNKcom.example.QLNK.provider.user.UserProvider;
import QLNKcom.example.QLNK.service.user.UserService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserService userService;
    private final UserProvider userProvider;
    private final AdafruitService adafruitService;
    private final CustomReactiveUserDetailsService customReactiveUserDetailsService;
    private final RedisService redisService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final EmailService emailService;

    @Value("${webapp_link}")
    private String RESET_LINK_BASE_URL;

    public Mono<AuthResponse> authenticate(LoginRequest request) {
        return customReactiveUserDetailsService.findByUsername(request.getEmail())
                .filter(userDetails -> passwordEncoder.matches(request.getPassword(), userDetails.getPassword()))
                .switchIfEmpty(Mono.error(new BadCredentialsException("Invalid credentials")))
                .flatMap(userDetails -> userProvider.findByEmail(request.getEmail()))
                .flatMap(user -> generateTokensAndCache(user)
                        .flatMap(response-> fetchAndStoreFeeds(response, user))
                )
                .doOnError(error -> System.err.println("❌ Error in login flow: " + error.getMessage()));
    }

    public Mono<AuthResponse> register(RegisterRequest request) {
        return userProvider.findByEmail(request.getEmail())
                .flatMap(existingUser -> Mono.error(new CustomAuthException("Email already in use", HttpStatus.BAD_REQUEST)))
                .onErrorResume(DataNotFoundException.class, ex ->
                        userService.createUser(request)
                                .flatMap(user -> generateTokensAndCache(user)
                                        .flatMap(response-> fetchAndStoreFeeds(response, user)))
                )
                .map(auth-> (AuthResponse) auth)
                .doOnError(error -> System.err.println("❌ Error in register flow: " + error.getMessage()));
    }


    public Mono<Void> logout(ServerHttpRequest request) {
        try {
            String token = jwtUtils.extractToken(request);
            String email = jwtUtils.extractAccessEmail(token);

            return redisService.deleteRefreshTokenIat(email)
                    .doOnError(error -> System.err.println("❌ Error in logout flow: " + error.getMessage()))
                    .then();
        } catch (CustomAuthException e) {
            System.err.println("❌ Error in logout flow: " + e.getMessage());
            return Mono.error(e);
        }
    }

    public Mono<AuthResponse> refresh(RefreshRequest request) {
        String refreshToken = request.getRefreshToken();

        Claims refreshClaim = jwtUtils.extractRefreshClaims(refreshToken);
        String email = refreshClaim.getSubject();
        long tokenIatSecond = refreshClaim.getIssuedAt().getTime() / 1000;

        return redisService.getRefreshTokenIat(email)
                .switchIfEmpty(Mono.error(new CustomAuthException("Invalid or revoked refresh token", HttpStatus.UNAUTHORIZED)))
                .flatMap(savedIat -> {
                    if (!savedIat.equals(tokenIatSecond)) {
                        return Mono.error(new CustomAuthException("Invalid or revoked refresh token", HttpStatus.UNAUTHORIZED));
                    }
                    String accessToken = jwtUtils.generateAccessToken(email);
                    return Mono.just(new AuthResponse(accessToken, refreshToken));  // ✅ Bọc vào Mono.just()
                });
    }

    private Mono<AuthResponse> generateTokensAndCache(User user) {

        String accessToken = jwtUtils.generateAccessToken(user.getEmail());
        String refreshToken = jwtUtils.generateRefreshToken(user.getEmail());
        return redisService.saveRefreshTokenIat(user.getEmail(), jwtUtils.extractRefreshIat(refreshToken).getTime() / 1000)
                .thenReturn(new AuthResponse(accessToken, refreshToken));  // ✅ Trả về AuthResponse
    }

    private Mono<AuthResponse> fetchAndStoreFeeds(AuthResponse response, User user) {
        return adafruitService.getUserGroups(user.getUsername(), user.getApikey())
                .flatMap(groups -> {
                    user.setGroups(groups);
                    return userProvider.saveUser(user);
                })
                .thenReturn(response)
                .onErrorMap(error -> new AdafruitException("Exception in connect to Adafruit", HttpStatus.BAD_REQUEST));
    }


    public Mono<Void> sendResetPasswordLink(String email) {
        return userProvider.findByEmail(email)
                .flatMap(user -> {
                    String token = UUID.randomUUID().toString();
                    String resetKey = "reset:" + token;
                    return redisService.saveResetPasswordToken(resetKey, email)
                            .then(emailService.sendEmail(
                                    email,
                                    "Reset Your Password",
                                    "Click the link to reset your password: " + RESET_LINK_BASE_URL + "?token=" + token +
                                            "\nThis link expires in 5 minutes."
                            ));
                });
    }

    public Mono<User> resetPasswordWithToken(String token, String newPassword) {
        String resetKey = "reset:" + token;
        return redisService.getResetPasswordToken(resetKey)
                .switchIfEmpty(Mono.error(new RuntimeException("Invalid or expired reset token")))
                .flatMap(email -> userProvider.findByEmail(email)
                        .switchIfEmpty(Mono.error(new RuntimeException("User not found")))
                        .flatMap(user -> {
                            user.setPassword(passwordEncoder.encode(newPassword));
                            return userProvider.saveUser(user)
                                    .doOnSuccess(u -> redisService.deletePassword(resetKey).subscribe());
                        }));
    }

}
