package com.inventra.api.report;

import com.inventra.api.exception.InvalidRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Focused unit tests for {@link ReportService#validateDateRange} boundary and
 * overflow behaviour. These run without a Spring context because the method has
 * no dependencies on the persistence layer.
 */
@DisplayName("ReportService.validateDateRange()")
class ReportServiceDateRangeTest {

    private final ReportService reportService = new ReportService();

    @Test
    @DisplayName("allows a span of exactly MAX_DATE_RANGE_DAYS")
    void allowsExactlyMaximumRange() {
        LocalDate start = LocalDate.of(2025, 1, 1);
        LocalDate end = start.plusDays(ReportConstants.MAX_DATE_RANGE_DAYS);

        assertThat(end.toEpochDay() - start.toEpochDay())
                .isEqualTo(ReportConstants.MAX_DATE_RANGE_DAYS);
        assertThatCode(() -> reportService.validateDateRange(start, end, "test report"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("rejects a span one day beyond MAX_DATE_RANGE_DAYS")
    void rejectsJustOverMaximumRange() {
        LocalDate start = LocalDate.of(2025, 1, 1);
        LocalDate end = start.plusDays(ReportConstants.MAX_DATE_RANGE_DAYS + 1);

        assertThatThrownBy(() -> reportService.validateDateRange(start, end, "test report"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Date range must not exceed");
    }

    @Test
    @DisplayName("does not overflow for a valid span with startDate near LocalDate.MAX")
    void handlesValidSpanNearLocalDateMax() {
        // startDate + MAX_DATE_RANGE_DAYS would overflow LocalDate.plusDays, even
        // though the actual span is tiny and valid.
        LocalDate start = LocalDate.MAX.minusDays(5);
        LocalDate end = LocalDate.MAX;

        assertThatCode(() -> reportService.validateDateRange(start, end, "test report"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("allows exactly the maximum span ending at LocalDate.MAX")
    void handlesMaximumSpanEndingAtLocalDateMax() {
        LocalDate start = LocalDate.MAX.minusDays(ReportConstants.MAX_DATE_RANGE_DAYS);
        LocalDate end = LocalDate.MAX;

        assertThatCode(() -> reportService.validateDateRange(start, end, "test report"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("rejects an over-limit span for dates near LocalDate.MAX")
    void rejectsOverLimitSpanNearLocalDateMax() {
        LocalDate start = LocalDate.MAX.minusDays(ReportConstants.MAX_DATE_RANGE_DAYS + 10);
        LocalDate end = LocalDate.MAX;

        assertThatThrownBy(() -> reportService.validateDateRange(start, end, "test report"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Date range must not exceed");
    }
}
