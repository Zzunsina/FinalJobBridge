package com.employment;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.employment.global.common.Database;
import com.employment.global.common.Problem;
import com.employment.job.service.EmploymentReferrals;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.SQLException;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.BadSqlGrammarException;

class EmploymentReferralSchemaTests {
    @Test
    void missingRecommendationTableDoesNotBreakExistingUserApplication() {
        Database db = mock(Database.class);
        when(db.list(anyString(), any(Object[].class))).thenThrow(new BadSqlGrammarException("query", "SELECT", new SQLException("Table employment_referrals doesn't exist", "42S02", 1146)));
        var service = new EmploymentReferrals(db, new ObjectMapper(), new com.employment.job.service.EmploymentInterviewProposals(db), new com.employment.job.service.EmploymentOnboarding(db));
        Map<String,Object> record = new HashMap<>(Map.of("employmentLinkId", 7L, "status", "REQUESTED"));
        service.decorateUser(record);
        assertThat(record).containsEntry("status", "REQUESTED").containsEntry("referralStatus", null);
        assertThatThrownBy(() -> service.companyList(1L)).isInstanceOf(Problem.class).hasMessageContaining("DB 설치가 필요");
    }
    @Test
    void unrelatedSqlErrorsAreNotSilenced() {
        Database db = mock(Database.class);
        var error = new BadSqlGrammarException("query", "SELECT", new SQLException("Unknown column interview_at", "42S22", 1054));
        when(db.list(anyString(), any(Object[].class))).thenThrow(error);
        assertThatThrownBy(() -> new EmploymentReferrals(db, new ObjectMapper(), new com.employment.job.service.EmploymentInterviewProposals(db), new com.employment.job.service.EmploymentOnboarding(db)).latest(7)).isSameAs(error);
    }
}
