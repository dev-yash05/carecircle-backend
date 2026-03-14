package com.carecircle.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

// 🧠 Binds app.* from application.yaml into one typed config class.
// This is cleaner than scattering @Value annotations across multiple classes.
//
// In application.yaml you need:
//   app:
//     super-admin-email: ${SUPER_ADMIN_EMAIL}
//
// In .env / Railway / environment:
//   SUPER_ADMIN_EMAIL=your.personal@gmail.com

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    // The one Google email that gets SUPER_ADMIN role automatically.
    // If the env var is missing, we default to empty string (nobody gets super admin).
    private String superAdminEmail = "";
}