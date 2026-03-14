package com.carecircle.config;

import com.carecircle.security.CareCircleOAuth2UserService;
import com.carecircle.security.JwtAuthFilter;
import com.carecircle.security.OAuth2SuccessHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity   // Enables @PreAuthorize on controllers
@RequiredArgsConstructor
public class SecurityConfig {

    private final CareCircleOAuth2UserService oAuth2UserService;
    private final OAuth2SuccessHandler        oAuth2SuccessHandler;
    private final JwtAuthFilter               jwtAuthFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth

                        // ── Completely public (no token needed) ──────────────
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/info",
                                "/login/**",
                                "/oauth2/**",
                                "/api/v1/auth/**",
                                "/swagger-ui/**",
                                "/v3/api-docs/**"
                        ).permitAll()

                        // ── SUPER_ADMIN only — locked down at URL level ──────
                        // @PreAuthorize("hasRole('SUPER_ADMIN')") on the controller
                        // is a second line of defence.
                        .requestMatchers("/api/v1/superadmin/**").hasRole("SUPER_ADMIN")

                        // ── Member management — Admin or SUPER_ADMIN ─────────
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/organizations/*/members").hasAnyRole("ADMIN", "SUPER_ADMIN")
                        .requestMatchers(HttpMethod.DELETE,
                                "/api/v1/organizations/*/members/*").hasAnyRole("ADMIN", "SUPER_ADMIN")
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/organizations/*/members").hasAnyRole("ADMIN", "SUPER_ADMIN")

                        // ── Everything else — any authenticated user ─────────
                        .anyRequest().authenticated()
                )

                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(userInfo ->
                                userInfo.userService(oAuth2UserService))
                        .successHandler(oAuth2SuccessHandler)
                )

                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:3000"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}