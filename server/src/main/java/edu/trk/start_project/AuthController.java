package edu.trk.start_project;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

// Єдиний користувач: його логін і пароль беруться зі змінних ADMIN_USERNAME / ADMIN_PASSWORD
@RestController
@RequestMapping("/auth")
public class AuthController {

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    private final JwtService jwt;
    private final String username;
    private final String password;

    public AuthController(JwtService jwt,
                          @Value("${app.auth.username}") String username,
                          @Value("${app.auth.password}") String password) {
        this.jwt = jwt;
        this.username = username;
        this.password = password;
    }

    // POST /auth/login: логін і пароль -> токен
    @PostMapping("/login")
    public Map<String, Object> login(@Valid @RequestBody LoginRequest req) {
        // & (а не &&): обидві перевірки виконуються завжди, щоб час відповіді не видавав, що саме не так
        boolean ok = sameText(req.username(), username) & sameText(req.password(), password);
        if (!ok) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("access_token", jwt.create(username));
        body.put("token_type", "bearer");
        body.put("expires_in", jwt.getExpireSeconds());
        body.put("username", username);
        return body;
    }

    // GET /auth/me: хто я (потрібен токен; логін кладе в запит AuthFilter)
    @GetMapping("/me")
    public Map<String, Object> me(@RequestAttribute(AuthFilter.USER_ATTR) String user) {
        return Map.of("username", user);
    }

    // Порівняння рядків за сталий час
    private static boolean sameText(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
