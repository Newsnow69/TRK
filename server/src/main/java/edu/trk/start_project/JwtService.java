package edu.trk.start_project;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

// Мінімальна реалізація JWT (HS256): токен = base64(заголовок).base64(дані).підпис
// Підпис = HMAC-SHA256 від перших двох частин із секретом JWT_SECRET.
// Хто не знає секрету, підробити токен не зможе. У великих проєктах беруть готову бібліотеку (jjwt),
// тут ми пишемо самі, щоб було видно, з чого токен складається.
@Service
public class JwtService {

    private static final Pattern SUB = Pattern.compile("\"sub\":\"([^\"]+)\"");
    private static final Pattern EXP = Pattern.compile("\"exp\":(\\d+)");

    private final byte[] key;
    private final long expireSeconds;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expire-minutes}") long expireMinutes) {
        this.key = secret.getBytes(StandardCharsets.UTF_8);
        this.expireSeconds = expireMinutes * 60;
    }

    public long getExpireSeconds() {
        return expireSeconds;
    }

    // Створює токен для користувача (логін має пройти перевірку в ConfigGuard: без лапок)
    public String create(String username) {
        long now = Instant.now().getEpochSecond();
        String header = encode("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String payload = encode("{\"sub\":\"" + username + "\",\"iat\":" + now
                + ",\"exp\":" + (now + expireSeconds) + "}");
        String unsigned = header + "." + payload;
        return unsigned + "." + sign(unsigned);
    }

    // Повертає логін, якщо токен справжній і не прострочений; інакше порожньо
    public Optional<String> verify(String token) {
        if (token == null) {
            return Optional.empty();
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3) {
            return Optional.empty();
        }
        try {
            // Порівняння за сталий час, щоб не підказувати підпис по часу відповіді
            byte[] expected = sign(parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII);
            byte[] actual = parts[2].getBytes(StandardCharsets.US_ASCII);
            if (!MessageDigest.isEqual(expected, actual)) {
                return Optional.empty();
            }
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            Matcher exp = EXP.matcher(payload);
            Matcher sub = SUB.matcher(payload);
            if (!exp.find() || !sub.find()) {
                return Optional.empty();
            }
            if (Instant.now().getEpochSecond() >= Long.parseLong(exp.group(1))) {
                return Optional.empty();   // термін дії вийшов
            }
            return Optional.of(sub.group(1));
        } catch (IllegalArgumentException e) {
            return Optional.empty();       // не base64 або сміття замість токена
        }
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 is not available", e);
        }
    }

    private static String encode(String text) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }
}
