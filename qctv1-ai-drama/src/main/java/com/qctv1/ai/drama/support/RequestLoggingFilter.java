package com.qctv1.ai.drama.support;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long startMillis = System.currentTimeMillis();
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String query = request.getQueryString();
        String remoteAddr = request.getRemoteAddr();
        log.info("[DRAMA_REQUEST_BEGIN] method={}, uri={}, query={}, remote={}", method, uri, query, remoteAddr);
        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMillis = System.currentTimeMillis() - startMillis;
            log.info(
                    "[DRAMA_REQUEST_END] method={}, uri={}, query={}, status={}, durationMs={}",
                    method,
                    uri,
                    query,
                    response.getStatus(),
                    durationMillis
            );
        }
    }
}
