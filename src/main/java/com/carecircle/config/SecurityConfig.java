package com.carecircle.config;

import com.carecircle.security.CareCircleOAuth2UserService;
import com.carecircle.security.JwtAuthFilter;
import com.carecircle.security.OAuth2SuccessHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

// =============================================================================
// 🧠 THIS IS THE MOST IMPORTANT SECURITY FILE.
// It wires everything together and defines the rules:
// - Which endpoints are public vs protected
// - How OAuth2 login works
// - Where our JWT filter sits in the chain
// - CORS rules for the Next.js frontend
// =============================================================================

@Configuration
@EnableWebSecurity
@EnableMethodSecurity   // Enables @PreAuthorize on controllers
@RequiredArgsConstructor
public class SecurityConfig {

    private final CareCircleOAuth2UserService oAuth2UserService;
    private final OAuth2SuccessHandler oAuth2SuccessHandler;
    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // ----------------------------------------------------------------
                // CSRF: Disabled because we use JWT in HttpOnly cookies.
                // 🧠 CSRF attacks work by tricking a browser into making a request
                // with the user's session cookie. Since our JWTs are HttpOnly and
                // we validate them ourselves, CSRF tokens add no extra security.
                // ----------------------------------------------------------------
                .csrf(csrf -> csrf.disable())

                // ----------------------------------------------------------------
                // CORS: Allow requests from our Next.js frontend
                // ----------------------------------------------------------------
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // ----------------------------------------------------------------
                // SESSION: STATELESS — we don't use server-side sessions.
                // JWTs carry all the state. This is essential for scalability.
                // ----------------------------------------------------------------
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // ----------------------------------------------------------------
                // AUTHORIZATION RULES
                // ----------------------------------------------------------------
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints — no token needed
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/info",
                                "/login/**",
                                "/oauth2/**",
                                "/api/v1/auth/**",
                                "/swagger-ui/**",
                                "/v3/api-docs/**"
                        ).permitAll()
                        // Everything else requires authentication
                        .anyRequest().authenticated()
                )

                // ----------------------------------------------------------------
                // OAUTH2 LOGIN: Google login configuration
                // ----------------------------------------------------------------
                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(userInfo ->
                                // Our custom service handles finding/creating users
                                userInfo.userService(oAuth2UserService)
                        )
                        // Called after successful Google login — issues our JWT
                        .successHandler(oAuth2SuccessHandler)
                )

                // ----------------------------------------------------------------
                // JWT FILTER: Runs before Spring's auth filter on every request
                // Reads access_token cookie → validates JWT → sets SecurityContext
                // ----------------------------------------------------------------
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // -------------------------------------------------------------------------
    // CORS Configuration
    // -------------------------------------------------------------------------
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // 🧠 allowedOrigins must match your frontend URL exactly
        config.setAllowedOrigins(List.of("http://localhost:3000"));

        // Allow standard HTTP methods
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));

        // Allow all headers (Content-Type, Authorization, etc.)
        config.setAllowedHeaders(List.of("*"));

        // 🧠 allowCredentials = true: Required for cookies to be sent cross-origin.
        // Without this, the browser won't send the access_token cookie to your API.
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}