package com.carecircle.security;

import com.carecircle.domain.user.User;
import com.carecircle.domain.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // 1. Extract access_token from cookies
        String token = extractTokenFromCookie(request);

        if (token != null && jwtService.isTokenValid(token)) {
            // 2. Extract user info from token (no DB call needed — JWT is self-contained)
            UUID userId = jwtService.extractUserId(token);
            String role = jwtService.extractRole(token);

            // 3. Load user from DB to verify they still exist and are active
            Optional<User> userOpt = userRepository.findById(userId);

            if (userOpt.isPresent() && userOpt.get().isActive()) {
                User user = userOpt.get();

                // 4. Create authentication object and put in SecurityContext
                // 🧠 This is what makes @PreAuthorize work downstream
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(
                                user,
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_" + role))
                        );
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }

        // 5. Always continue the filter chain — security rules applied by SecurityConfig
        filterChain.doFilter(request, response);
    }

    private String extractTokenFromCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies())
                .filter(cookie -> "access_token".equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}