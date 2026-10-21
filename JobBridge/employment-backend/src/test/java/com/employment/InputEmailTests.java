package com.employment;

import com.employment.global.common.Input;
import com.employment.global.common.Problem;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class InputEmailTests {
    @Test
    void rejectsMalformedAddresses() {
        for (String email : new String[]{"plain", "name@", "@gmail.com", "a@@gmail.com", "a b@gmail.com", ".name@gmail.com", "name.@gmail.com", "a..b@gmail.com", "name@gmail", "name@-gmail.com", "name@gmail-.com", "name@g_mail.com", "name@gmail..com", "name@gmail.com.", "name@gmail.c", "name@gmail.com ", "a".repeat(65) + "@gmail.com"}) {
            assertThatThrownBy(() -> Input.email(Map.of("email", email), "email")).as(email).isInstanceOf(Problem.class);
        }
    }

    @Test
    void acceptsSelectedAndCustomDomains() {
        for (String email : new String[]{"name@naver.com", "a.b+tag@gmail.com", "name@company.co.kr", "name@sub-domain.example.com", "name@test.local"}) {
            assertThat(Input.email(Map.of("email", email), "email")).isEqualTo(email);
        }
        assertThat(Input.email(Map.of("email", "NAME@GMAIL.COM"), "email")).isEqualTo("name@gmail.com");
    }
}
