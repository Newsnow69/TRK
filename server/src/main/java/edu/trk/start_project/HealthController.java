package edu.trk.start_project;

import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// @RestController: методи повертають дані (JSON), а не HTML-сторінки
@RestController
public class HealthController {

    private final DataSource dataSource;   // підключення до БД, його створює Spring
    private final String version;          // з app.version
    private final String env;              // з app.env

    // Spring сам підставить DataSource і значення з application.properties
    public HealthController(DataSource dataSource,
                            @Value("${app.version}") String version,
                            @Value("${app.env}") String env) {
        this.dataSource = dataSource;
        this.version = version;
        this.env = env;
    }

    // GET /health: перевірка для Docker healthcheck, CI та балансувальника
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        boolean dbUp = false;
        String dbName = "unknown";
        // try-with-resources сам поверне з'єднання в пул
        try (Connection c = dataSource.getConnection()) {
            dbUp = c.isValid(2);                                // 2 секунди на відповідь
            dbName = c.getMetaData().getDatabaseProductName(); // "H2" або "MySQL"
        } catch (Exception ignored) {
            // БД недоступна: dbUp залишається false
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", dbUp ? "ok" : "degraded");
        body.put("version", version);
        body.put("env", env);
        body.put("database", dbUp ? "up" : "down");
        body.put("database_mode", dbName);

        // 200, якщо БД жива; 503, якщо ні (саме це бачитиме CI та healthcheck)
        return ResponseEntity.status(dbUp ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }
}