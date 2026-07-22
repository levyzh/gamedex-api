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

// @Configuration = this class defines beans (objects Spring manages).
// Spring Security looks for a SecurityFilterChain bean to know the rules;
// if we define one, it replaces the "lock everything" default.
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Wire in the CORS rules defined in the bean below. This also
                // makes Spring Security answer the browser's preflight OPTIONS
                // requests itself, without demanding a JWT (preflights never
                // carry tokens — a locked preflight would block everything).
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // Rules are checked top-down, first match wins — same idea as
                // RLS policies OR-ing together, but ordered.
                .authorizeHttpRequests(auth -> auth
                        // Public reads: anyone may check health or read comments
                        // (mirrors our "select everyone" RLS policy).
                        .requestMatchers("/api/health").permitAll()
                        // HttpMethod.GET is the important part: a path-only rule
                        // opens EVERY method (POST/PUT/DELETE too). Now only
                        // reads are public; writes on this same path fall
                        // through to anyRequest() below and need a JWT.
                        .requestMatchers(HttpMethod.GET, "/api/games/*/comments").permitAll()
                        // Follows READS are public too — a profile page (its
                        // follower counts and lists) must work logged-out. The
                        // WRITES (POST/DELETE /api/users/*/follow) are NOT listed
                        // here, so they fall through to anyRequest() and need a JWT.
                        .requestMatchers(HttpMethod.GET, "/api/users/*/follow-stats").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/users/*/followers").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/users/*/following").permitAll()
                        // List READ is public (Stage D public-read: friends'
                        // lists must show logged-out). The list WRITES live on a
                        // different path (PUT/DELETE /api/list/*) and are NOT
                        // listed, so they fall through to anyRequest() -> JWT.
                        .requestMatchers(HttpMethod.GET, "/api/users/*/list").permitAll()
                        // Everything else needs a valid Supabase JWT.
                        .anyRequest().authenticated()
                )
                // Validate bearer tokens as JWTs using the jwk-set-uri from
                // application.properties.
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}))
                // CSRF protection defends cookie-based sessions; we use bearer
                // tokens, not cookies, so it's off — standard for token APIs.
                .csrf(csrf -> csrf.disable());

        return http.build();
    }

    // The CORS policy: which browser origins may call this API, with what.
    // This is about BROWSERS only — curl and other servers ignore CORS
    // entirely. It's the browser that reads these headers and decides
    // whether to let the page's JavaScript proceed.
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // Exactly who may call us: the Vite dev server. When the frontend
        // deploys, its real origin gets added to this list. Never "*" —
        // a wildcard would tell browsers ANY website may call this API
        // with the user's credentials.
        config.setAllowedOrigins(List.of("http://localhost:5173"));

        // The methods we actually serve. OPTIONS is the preflight itself.
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));

        // Headers the frontend is allowed to attach. Authorization is the
        // critical one — without it listed here, the browser would refuse
        // to send the JWT and every authed call would arrive tokenless.
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));

        // How long (seconds) the browser may cache a preflight answer, so
        // it doesn't re-ask before every single request. 1 hour.
        config.setMaxAge(3600L);

        // Apply these rules to every path in the API.
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}