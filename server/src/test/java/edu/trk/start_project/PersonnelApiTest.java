package edu.trk.start_project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

// Інтеграційний тест: запускає справжній застосунок на випадковому порту (з базою H2 у пам'яті)
// і ходить до нього справжніми HTTP-запитами, так само, як це робитимуть браузер та curl.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PersonnelApiTest {

    @Value("${local.server.port}")
    private int port;

    private final HttpClient client = HttpClient.newHttpClient();

    // ---------- допоміжні методи ----------

    private HttpResponse<String> send(String method, String path, String token, String json) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json");
        if (token != null) {
            b.header("Authorization", "Bearer " + token);
        }
        b.method(method, json == null ? BodyPublishers.noBody()
                : BodyPublishers.ofString(json, StandardCharsets.UTF_8));
        return client.send(b.build(), BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private String login() throws Exception {
        HttpResponse<String> r = send("POST", "/auth/login", null, "{\"username\":\"admin\",\"password\":\"admin\"}");
        assertEquals(200, r.statusCode());
        return find(r.body(), "\"access_token\":\"([^\"]+)\"");
    }

    private static String find(String text, String regex) {
        Matcher m = Pattern.compile(regex).matcher(text);
        assertTrue(m.find(), "pattern not found: " + regex + " in " + text);
        return m.group(1);
    }

    // ---------- відкриті адреси ----------

    @Test
    void healthIsOpenAndReportsDatabase() throws Exception {
        HttpResponse<String> r = send("GET", "/health", null, null);
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("\"status\":\"ok\""));
        assertTrue(r.body().contains("\"database\":\"up\""));
    }

    @Test
    void healthIsAlsoReachableThroughApiPrefix() throws Exception {
        assertEquals(200, send("GET", "/api/health", null, null).statusCode());
    }

    // ---------- вхід і захист ----------

    @Test
    void personnelRequiresToken() throws Exception {
        assertEquals(401, send("GET", "/personnel", null, null).statusCode());
        assertEquals(401, send("GET", "/api/personnel", null, null).statusCode());
        assertEquals(401, send("GET", "/personnel", "not-a-real-token", null).statusCode());
    }

    @Test
    void loginRejectsWrongPassword() throws Exception {
        HttpResponse<String> r = send("POST", "/auth/login", null, "{\"username\":\"admin\",\"password\":\"wrong\"}");
        assertEquals(401, r.statusCode());
    }

    @Test
    void meReturnsLoggedInUser() throws Exception {
        HttpResponse<String> r = send("GET", "/auth/me", login(), null);
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("\"username\":\"admin\""));
    }

    // ---------- облік особового складу ----------

    @Test
    void fullCycleCreateFindUpdateDelete() throws Exception {
        String token = login();

        // створення: 201 і виданий id
        HttpResponse<String> created = send("POST", "/personnel", token,
                "{\"full_name\":\"Test Person Alpha\",\"rank\":\"sergeant\",\"unit\":\"Test Unit\",\"status\":\"present\"}");
        assertEquals(201, created.statusCode());
        String id = find(created.body(), "\"id\":(\\d+)");

        // пошук за частиною імені
        HttpResponse<String> found = send("GET", "/personnel?search=alpha", token, null);
        assertEquals(200, found.statusCode());
        assertTrue(found.body().contains("Test Person Alpha"));

        // зміна статусу
        HttpResponse<String> updated = send("PUT", "/personnel/" + id, token,
                "{\"full_name\":\"Test Person Alpha\",\"rank\":\"sergeant\",\"unit\":\"Test Unit\",\"status\":\"sick\"}");
        assertEquals(200, updated.statusCode());
        assertTrue(updated.body().contains("\"status\":\"sick\""));

        // зведення містить усі п'ять статусів
        HttpResponse<String> summary = send("GET", "/personnel/summary", token, null);
        assertEquals(200, summary.statusCode());
        assertNotNull(find(summary.body(), "\"sick\":(\\d+)"));

        // видалення: 204, а далі запис уже не знаходиться
        assertEquals(204, send("DELETE", "/personnel/" + id, token, null).statusCode());
        assertEquals(404, send("GET", "/personnel/" + id, token, null).statusCode());
    }

    @Test
    void invalidDataIsRejectedWith400() throws Exception {
        String token = login();
        // порожнє ім'я
        assertEquals(400, send("POST", "/personnel", token, "{\"full_name\":\"\"}").statusCode());
        // неіснуючий статус
        assertEquals(400, send("POST", "/personnel", token,
                "{\"full_name\":\"Bad Status\",\"status\":\"dancing\"}").statusCode());
    }

    @Test
    void personnelWorksThroughApiPrefix() throws Exception {
        assertEquals(200, send("GET", "/api/personnel", login(), null).statusCode());
    }
}
