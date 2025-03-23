package QLNKcom.example.QLNK.config.security;

import QLNKcom.example.QLNK.config.jwt.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.security.web.server.context.WebSessionServerSecurityContextRepository;
import org.springframework.web.cors.CorsConfiguration;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebFluxSecurity
@RequiredArgsConstructor
public class WebSecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityWebFilterChain securityFilterChain(ServerHttpSecurity http, ServerSecurityContextRepository securityContextRepository) {
        http.authorizeExchange(auth -> auth
                .pathMatchers("/", "/auth/**").permitAll()
                .pathMatchers("/auth/logout").authenticated()
                .anyExchange().authenticated()
        );
        http.addFilterBefore(jwtAuthenticationFilter, SecurityWebFiltersOrder.AUTHENTICATION);
        http.securityContextRepository(securityContextRepository);
        http.csrf(ServerHttpSecurity.CsrfSpec::disable);
        http.formLogin(ServerHttpSecurity.FormLoginSpec::disable);

        http.cors(cors -> cors.configurationSource(request -> {
            CorsConfiguration config = new CorsConfiguration();
            config.setAllowedOrigins(List.of("http://localhost:3000")); // Frontend origin
            config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
            config.setAllowedHeaders(List.of("*"));
            config.setAllowCredentials(true);
            return config;
        }));

        return http.build();
    }

    @Bean
    public ServerSecurityContextRepository serverSecurityContextRepository() {
        return new WebSessionServerSecurityContextRepository();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

}
