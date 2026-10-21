package com.employment;

import com.employment.global.common.Input;
import com.employment.global.common.Problem;
import java.time.*;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class InputBirthDateTests {
    @Test
    void acceptsBothInclusiveBoundaries() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        for (LocalDate date : new LocalDate[]{today, today.minusYears(100)}) {
            assertThat(Input.birthDate(date.format(DateTimeFormatter.BASIC_ISO_DATE))).isEqualTo(date);
            assertThat(Input.birthDate(date.toString())).isEqualTo(date);
        }
    }
    @Test
    void rejectsOutsideBoundariesAndImpossibleDates() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        for (String date : new String[]{today.minusYears(100).minusDays(1).toString(), today.plusDays(1).toString(), "20010229", "20001301", "", "00000101"}) {
            assertThatThrownBy(() -> Input.birthDate(date)).as(date).isInstanceOf(Problem.class);
        }
    }
}
