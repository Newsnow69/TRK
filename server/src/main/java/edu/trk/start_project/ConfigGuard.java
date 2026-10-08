package edu.trk.start_project;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// Перевіряє налаштування при старті. Якщо щось небезпечне, застосунок не запуститься зовсім
// (у Docker це буде видно в логах одразу, а не після зламу).
@Component
public class ConfigGuard {

    public ConfigGuard(@Value("${app.env}") String env,
                       @Value("${app.auth.username}") String username,
                       @Value("${app.auth.password}") String password,
                       @Value("${app.jwt.secret}") String secret) {
        // Логін потрапляє всередину токена, тому лапок та інших символів бути не повинно
        if (!username.matches("[A-Za-z0-9_.@-]{1,64}")) {
            throw new IllegalStateException("ADMIN_USERNAME: only letters, digits and _ . @ - are allowed");
        }
        if (secret.length() < 32) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 characters long");
        }
        // У production значення за замовчуванням (admin / dev-only-...) не припустимі
        if ("production".equalsIgnoreCase(env)
                && (secret.startsWith("dev-only") || password.equals("admin") || password.length() < 8)) {
            throw new IllegalStateException(
                    "APP_ENV=production: set a real JWT_SECRET and an ADMIN_PASSWORD of at least 8 characters");
        }
    }
}
