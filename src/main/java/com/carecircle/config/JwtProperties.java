package com.carecircle.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

// 🧠 @ConfigurationProperties: Binds app.jwt.* from application.yml
// to this class automatically. Clean alternative to @Value("${app.jwt.secret}").
// Benefit: all JWT config is in one place, not scattered across classes.

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {
    private String secret;
    private long expiryMs;
    private long refreshExpiryMs;
}