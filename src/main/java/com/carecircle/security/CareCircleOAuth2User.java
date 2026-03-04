package com.carecircle.security;

import com.carecircle.domain.user.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class CareCircleOAuth2User implements OAuth2User {

    @Getter
    private final User user;
    private final Map<String, Object> attributes;

    public CareCircleOAuth2User(User user, Map<String, Object> attributes) {
        this.user = user;
        this.attributes = attributes;
    }

    @Override
    public Map<String, Object> getAttributes() { return attributes; }

    @Override
    public String getName() { return user.getEmail(); }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // 🧠 "ROLE_" prefix is required by Spring Security for hasRole() checks
        return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
    }
}
