package edu.trk.start_project;

import java.io.IOException;
import java.util.Optional;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

// Охоронець: перед кожним запитом до /personnel та /auth/me перевіряє заголовок
//   Authorization: Bearer <токен>
// Усе інше (/health, /auth/login, сторінка) відкрите без токена.
// @Order(10): цей фільтр іде ПЕРШИМ (ApiPrefixFilter має 20), тож бачить початкову адресу /api/...
@Component
@Order(10)
public class AuthFilter extends OncePerRequestFilter {

    // Під цим ім'ям фільтр передає логін далі, у контролер
    public static final String USER_ATTR = "auth.user";

    private final JwtService jwt;

    public AuthFilter(JwtService jwt) {
        this.jwt = jwt;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path.startsWith("/api/")) {
            path = path.substring(4);   // /api/personnel -> /personnel
        }
        boolean protectedPath = path.equals("/personnel") || path.startsWith("/personnel/")
                || path.equals("/auth/me");
        if (!protectedPath) {
            chain.doFilter(request, response);
            return;
        }

        String header = request.getHeader("Authorization");
        Optional<String> user = Optional.empty();
        if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            user = jwt.verify(header.substring(7).trim());
        }
        if (user.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);   // 401
            response.setHeader("WWW-Authenticate", "Bearer");
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                    "{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Authentication required: missing or invalid token\"}");
            return;
        }
        request.setAttribute(USER_ATTR, user.get());
        chain.doFilter(request, response);
    }
}
