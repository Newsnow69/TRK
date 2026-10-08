package edu.trk.start_project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

// Звичайний модульний тест: Spring не запускається, тому проходить за долі секунди
class JwtServiceTest {

    private static final String SECRET = "test-secret-test-secret-test-secret-123";

    @Test
    void validTokenReturnsUsername() {
        JwtService jwt = new JwtService(SECRET, 5);
        String token = jwt.create("admin");
        assertEquals(Optional.of("admin"), jwt.verify(token));
    }

    @Test
    void tokenHasThreeParts() {
        JwtService jwt = new JwtService(SECRET, 5);
        assertEquals(3, jwt.create("admin").split("\\.").length);
    }

    @Test
    void tamperedTokenIsRejected() {
        JwtService jwt = new JwtService(SECRET, 5);
        String[] p = jwt.create("admin").split("\\.");
        // Підміна середньої частини (даних) ламає підпис
        String forged = p[0] + "." + p[1].substring(0, p[1].length() - 2) + "xx." + p[2];
        assertTrue(jwt.verify(forged).isEmpty());
    }

    @Test
    void tokenFromAnotherSecretIsRejected() {
        String token = new JwtService(SECRET, 5).create("admin");
        JwtService other = new JwtService("another-secret-another-secret-12345", 5);
        assertTrue(other.verify(token).isEmpty());
    }

    @Test
    void expiredTokenIsRejected() {
        JwtService jwt = new JwtService(SECRET, -1);   // термін дії вже в минулому
        assertTrue(jwt.verify(jwt.create("admin")).isEmpty());
    }

    @Test
    void garbageIsRejected() {
        JwtService jwt = new JwtService(SECRET, 5);
        assertTrue(jwt.verify(null).isEmpty());
        assertTrue(jwt.verify("").isEmpty());
        assertTrue(jwt.verify("abc").isEmpty());
        assertTrue(jwt.verify("a.b.c").isEmpty());
    }
}
