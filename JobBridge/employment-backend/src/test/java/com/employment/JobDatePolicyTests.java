package com.employment.job.service;
import static org.assertj.core.api.Assertions.*;
import com.employment.global.common.Problem;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
class JobDatePolicyTests {
    final LocalDate today=LocalDate.of(2026,10,27);
    @Test void newJobCannotBackdateStart() {
        assertThatThrownBy(()->JobDatePolicy.validate(today.minusDays(1),today.plusDays(1),null,today)).isInstanceOf(Problem.class);
    }
    @Test void todayAndFutureAreAllowed() {
        assertThatCode(()->JobDatePolicy.validate(today,today,null,today)).doesNotThrowAnyException();
        assertThatCode(()->JobDatePolicy.validate(today.plusDays(1),today.plusDays(2),null,today)).doesNotThrowAnyException();
    }
    @Test void pastDeadlineAndReversedRangeAreRejected() {
        assertThatThrownBy(()->JobDatePolicy.validate(today.minusDays(2),today.minusDays(1),today.minusDays(2),today)).isInstanceOf(Problem.class);
        assertThatThrownBy(()->JobDatePolicy.validate(today.plusDays(2),today.plusDays(1),null,today)).isInstanceOf(Problem.class);
    }
    @Test void existingStartedJobCanRetainOriginalDateButCannotBackdateToAnotherDate() {
        var original=today.minusDays(6);
        assertThatCode(()->JobDatePolicy.validate(original,today,original,today)).doesNotThrowAnyException();
        assertThatThrownBy(()->JobDatePolicy.validate(today.minusDays(2),today,original,today)).isInstanceOf(Problem.class);
        assertThatCode(()->JobDatePolicy.validate(today,today,original,today)).doesNotThrowAnyException();
    }
}
