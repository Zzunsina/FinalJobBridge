package com.employment.global.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;
import org.springframework.web.filter.OncePerRequestFilter;

final class PageReloadFilter extends OncePerRequestFilter {
    private static final String COOKIE = "jobbridge_browser";
    private final Map<String, Visits> visits = new HashMap<>();
    private final LongSupplier clock;
    private final boolean secure;

    PageReloadFilter(boolean secure) { this(secure, System::currentTimeMillis); }

    PageReloadFilter(boolean secure, LongSupplier clock) {
        this.secure = secure;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws IOException, ServletException {
        if (!request.getRequestURI().startsWith("/api/")) {
            chain.doFilter(request, response);
            return;
        }
        boolean entry = request.getMethod().equals("GET") && request.getRequestURI().equals("/api/auth/page-entry");
        String browser = null;
        if (request.getCookies() != null) for (Cookie cookie : request.getCookies()) {
            if (COOKIE.equals(cookie.getName()) && cookie.getValue().matches("[a-f0-9-]{36}")) browser = cookie.getValue();
        }
        if (entry && browser == null) {
            browser = UUID.randomUUID().toString();
            response.addHeader("Set-Cookie", COOKIE + "=" + browser + "; Path=/; HttpOnly; SameSite=Lax; Max-Age=31536000" + (secure ? "; Secure" : ""));
        }
        long retry = 0;
        long now = clock.getAsLong();
        synchronized (visits) {
            visits.entrySet().removeIf(item -> item.getValue().blockedUntil <= now && item.getValue().lastSeen < now - 60000);
            Visits state = browser == null ? null : visits.get(browser);
            if (entry && state == null) {
                // Bound memory used by anonymous browser identities.
                if (visits.size() >= 10000) visits.remove(visits.keySet().iterator().next());
                state = new Visits();
                visits.put(browser, state);
            }
            if (state != null) {
                state.lastSeen = now;
                if (state.blockedUntil > now) {
                    retry = (state.blockedUntil - now + 999) / 1000;
                } else if (entry && "true".equals(request.getParameter("reload"))) {
                    state.reloads.removeIf(time -> time <= now - 30000);
                    state.reloads.addLast(now);
                    if (state.reloads.size() > 10) {
                        state.blockedUntil = now + 60000;
                        state.reloads.clear();
                        retry = 60;
                    }
                }
            }
        }
        if (entry || retry > 0) {
            response.setHeader("Cache-Control", "no-store");
            response.setContentType("application/json;charset=UTF-8");
            if (retry > 0) {
                response.setStatus(429);
                response.setHeader("Retry-After", Long.toString(retry));
                response.getWriter().write("{\"code\":\"PAGE_RELOAD_LIMIT\",\"message\":\"새로고침 요청이 너무 많습니다. 잠시 후 다시 이용해 주세요.\",\"retryAfter\":" + retry + "}");
            } else response.getWriter().write("{\"allowed\":true}");
            return;
        }
        chain.doFilter(request, response);
    }

    private static final class Visits {
        final ArrayDeque<Long> reloads = new ArrayDeque<>();
        long blockedUntil;
        long lastSeen;
    }
}
