package com.employment.auth;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.employment.global.common.Database;
import com.employment.global.common.Problem;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EmailAvailabilityTests {
    final Database db = mock(Database.class);
    final AuthService auth = new AuthService(db, null, "test-secret-at-least-thirty-two-characters-long", false, null, null);

    @Test void checksNormalizedEmailAndReportsDuplicate() {
        when(db.list("SELECT user_id FROM users WHERE LOWER(email)=?", "user@example.com"))
            .thenReturn(List.of(Map.of("userId", 1L)));
        assertThat(auth.emailAvailability("USER@EXAMPLE.COM"))
            .containsEntry("available", false).containsEntry("message", "이미 사용 중인 이메일입니다.");
        assertThat(auth.emailAvailability("new@example.com")).containsEntry("available", true);
    }

    @Test void rejectsInvalidEmailBeforeQueryingDatabase() {
        assertThatThrownBy(() -> auth.emailAvailability("not-an-email")).isInstanceOf(Problem.class);
        verifyNoInteractions(db);
    }

    @Test void signupRejectsDuplicateEmailForBothMemberKindsBeforeInserting() {
        when(db.list("SELECT user_id FROM users WHERE LOWER(email)=?", "user@example.com"))
            .thenReturn(List.of(Map.of("userId", 1L)));
        for (boolean company : new boolean[] { false, true }) {
            assertThatThrownBy(() -> auth.signup(Map.of("loginId", "newuser", "email", "USER@example.com"), company))
                .isInstanceOfSatisfying(Problem.class, error -> {
                    assertThat(error.status).isEqualTo(409);
                    assertThat(error.field).isEqualTo("email");
                    assertThat(error.getMessage()).isEqualTo("이미 사용 중인 이메일입니다.");
                });
        }
        verify(db, never()).insert(anyString(), anyMap());
    }
}
