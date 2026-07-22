package com.gamedex.gamedex_api;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/games/*/comments").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/users/*/follow-stats").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/users/*/followers").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/users/*/following").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/users/*/list").permitAll()
                        // Profile READS are public: a user page and the People
                        // search must work logged-out. The profile WRITES live on
                        // /api/me/profile and /api/me/avatar (NOT listed here), so
                        // they fall through to anyRequest() and need a JWT.
                        // ("/api/users/search" is two segments, so it can't be
                        // mistaken for a "/api/users/*/..." three-segment path.)
                        .requestMatchers(HttpMethod.GET, "/api/users/*/profile").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/users/search").permitAll()
                        // Everything else needs a valid Supabase JWT.
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}))
                .csrf(csrf -> csrf.disable());

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:5173"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}