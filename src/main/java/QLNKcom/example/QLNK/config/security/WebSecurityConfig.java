package QLNKcom.example.QLNK.config.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class WebSecurityConfig {
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers( "/", "/auth/**").permitAll()
                .requestMatchers("/ws/**").permitAll()
                .requestMatchers("/auth/logout", "/auth/refresh-token").authenticated()
                .anyRequest().authenticated()
        );

        http.csrf(AbstractHttpConfigurer::disable);

        return http.build();
    }
}
