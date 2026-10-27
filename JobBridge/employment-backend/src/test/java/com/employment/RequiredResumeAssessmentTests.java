package com.employment;

import static com.employment.global.common.Database.*;
import static org.assertj.core.api.Assertions.*;
import com.employment.global.common.Database;
import com.employment.global.common.Problem;
import com.employment.job.service.EmploymentService;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class RequiredResumeAssessmentTests {
    @Autowired Database db;
    @Autowired EmploymentService service;
    long user;
    static final List<Integer> ANSWERS = List.of(2,1,1,2,2,1,2,0,2,2);

    @BeforeEach void setup() {
        String unique = UUID.randomUUID().toString().replace("-", "");
        user = db.insert("users", fields("login_id", "quiz" + unique.substring(0, 12),
            "email", unique + "@example.invalid", "password", "test-only-unusable-password",
            "name", "검사 테스트", "phone", "010" + String.format("%08d", Math.abs(unique.hashCode()) % 100000000),
            "birth_date", LocalDate.of(2000,1,1), "gender", "MALE", "role", "JOB_SEEKER"));
        db.insert("job_seekers", fields("user_id", user));
    }

    @Test void missingOrPartialOrForgedAssessmentCannotCreateResume() {
        for (var body : List.of(fields("title", "검사 없음"),
            fields("title", "일부 응답", "assessmentAnswers", List.of(0)),
            fields("title", "점수 위조", "assessment", fields("score", 100)))) {
            assertThatThrownBy(() -> service.saveResume(user, null, body)).isInstanceOf(Problem.class);
            assertThat(service.resumes(user)).isEmpty();
        }
    }

    @Test void completeAssessmentAllowsSaveAndResultSurvivesEditing() {
        var saved = service.saveResume(user, null, fields("title", "검사 완료", "assessmentAnswers", ANSWERS));
        long id = number(saved, "resumeId");
        assertThat(((Map<?,?>) saved.get("assessment")).get("score")).isEqualTo(100);
        var updated = service.saveResume(user, id, fields("title", "제목 수정"));
        assertThat(updated.get("title")).isEqualTo("제목 수정");
        assertThat(((Map<?,?>) updated.get("assessment")).get("score")).isEqualTo(100);
    }

    @Test void legacyResumeRequiresAssessmentBeforeEditingAndCannotUseAnotherUsersResult() {
        long seeker = number(db.one("SELECT job_seeker_id FROM job_seekers WHERE user_id=?", user), "jobSeekerId");
        long id = db.insert("resumes", fields("job_seeker_id", seeker, "title", "기존 이력서"));
        assertThatThrownBy(() -> service.saveResume(user, id, fields("title", "검사 없이 수정"))).isInstanceOf(Problem.class);
        assertThat(service.resume(user, id).get("title")).isEqualTo("기존 이력서");
        var saved = service.saveResume(user, id, fields("title", "검사 후 수정", "assessmentAnswers", ANSWERS));
        assertThat(((Map<?,?>) saved.get("assessment")).get("score")).isEqualTo(100);
        assertThatThrownBy(() -> service.saveResume(user, null, fields("title", "다른 이력서"))).isInstanceOf(Problem.class);
    }
}
