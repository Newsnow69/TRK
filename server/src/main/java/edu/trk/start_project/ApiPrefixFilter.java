package edu.trk.start_project;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;


@Component
@Order(20)
public class ApiPrefixFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String uri = request.getRequestURI();
        if (uri.startsWith("/api/")) {
            // forward: внутрішнє перенаправлення на ту саму адресу без /api (параметри запиту зберігаються)
            request.getRequestDispatcher(uri.substring(4)).forward(request, response);
            return;
        }
        chain.doFilter(request, response);
    }
}