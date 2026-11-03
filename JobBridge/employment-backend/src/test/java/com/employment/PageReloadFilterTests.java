package com.employment.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class PageReloadFilterTests {
    final AtomicLong time = new AtomicLong(100000);
    final PageReloadFilter filter = new PageReloadFilter(false, time::get);
    final String browser = "12345678-1234-1234-1234-123456789abc";

    MockHttpServletResponse request(String id, String path, boolean reload) throws Exception {
        var request = new MockHttpServletRequest("GET", path);
        if (id != null) request.setCookies(new Cookie("jobbridge_browser", id));
        request.setParameter("reload", Boolean.toString(reload));
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> res.getWriter().write("passed"));
        return response;
    }

    @Test void blocksEleventhReloadAndApisWithoutExtendingTheMinute() throws Exception {
        for (int i = 0; i < 10; i++) assertThat(request(browser, "/api/auth/page-entry", true).getStatus()).isEqualTo(200);
        var blocked = request(browser, "/api/auth/page-entry", true);
        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isEqualTo("60");
        assertThat(request(browser, "/api/jobs", false).getStatus()).isEqualTo(429);
        time.addAndGet(30000);
        assertThat(request(browser, "/api/auth/page-entry", true).getHeader("Retry-After")).isEqualTo("30");
        time.addAndGet(30000);
        assertThat(request(browser, "/api/auth/page-entry", false).getStatus()).isEqualTo(200);
        assertThat(request(browser, "/api/jobs", false).getContentAsString()).isEqualTo("passed");
    }

    @Test void ordinaryRequestsDoNotCountAndOtherBrowsersOnSameIpRemainAllowed() throws Exception {
        for (int i = 0; i < 30; i++) {
            request(browser, "/api/jobs", false);
            request(browser, "/api/auth/page-entry", false);
        }
        for (int i = 0; i < 10; i++) assertThat(request(browser, "/api/auth/page-entry", true).getStatus()).isEqualTo(200);
        assertThat(request(browser, "/api/auth/page-entry", true).getStatus()).isEqualTo(429);
        assertThat(request("aaaaaaaa-1234-1234-1234-123456789abc", "/api/auth/page-entry", true).getStatus()).isEqualTo(200);
        assertThat(request(null, "/api/auth/page-entry", false).getHeader("Set-Cookie")).contains("HttpOnly", "SameSite=Lax");
    }

    @Test void reloadsOutsideTheRollingWindowDoNotCount() throws Exception {
        for (int i = 0; i < 10; i++) request(browser, "/api/auth/page-entry", true);
        time.addAndGet(30000);
        assertThat(request(browser, "/api/auth/page-entry", true).getStatus()).isEqualTo(200);
    }
}
