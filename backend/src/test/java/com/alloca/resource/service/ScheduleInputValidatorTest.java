package com.alloca.resource.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.alloca.common.api.ApiException;
import com.alloca.common.api.ErrorCode;
import com.alloca.resource.domain.ScheduleWindow;
import com.alloca.resource.dto.ResourceScheduleReplaceRequest;
import com.alloca.resource.dto.ResourceScheduleReplaceRequest.Window;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScheduleInputValidatorTest {

    private static ResourceScheduleReplaceRequest request(Window... windows) {
        return new ResourceScheduleReplaceRequest(List.of(windows));
    }

    private static Window window(DayOfWeek day, String start, String end) {
        return new Window(day, start, end);
    }

    @Test
    void acceptsValidWindowsWithAndWithoutSeconds() {
        List<ScheduleWindow> windows = ScheduleInputValidator.toWindows(request(
                window(DayOfWeek.MONDAY, "08:00", "12:00:30"),
                window(DayOfWeek.TUESDAY, "09:15:00", "17:45")));

        assertThat(windows).containsExactly(
                new ScheduleWindow(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(12, 0, 30)),
                new ScheduleWindow(DayOfWeek.TUESDAY, LocalTime.of(9, 15), LocalTime.of(17, 45)));
    }

    @Test
    void touchingWindowsOnTheSameDayAreAllowed() {
        assertThat(ScheduleInputValidator.toWindows(request(
                window(DayOfWeek.MONDAY, "08:00", "12:00"),
                window(DayOfWeek.MONDAY, "12:00", "17:00")))).hasSize(2);
    }

    @Test
    void sameTimesOnDifferentDaysDoNotOverlap() {
        assertThat(ScheduleInputValidator.toWindows(request(
                window(DayOfWeek.MONDAY, "08:00", "12:00"),
                window(DayOfWeek.TUESDAY, "08:00", "12:00")))).hasSize(2);
    }

    @Test
    void rejectsStartNotBeforeEnd() {
        assertThatThrownBy(() -> ScheduleInputValidator.toWindows(request(
                window(DayOfWeek.MONDAY, "08:00", "12:00"),
                window(DayOfWeek.TUESDAY, "17:00", "09:00"),
                window(DayOfWeek.WEDNESDAY, "10:00", "10:00"))))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(ex.getFieldErrors()).extracting("field")
                            .containsExactly("schedules[1].endTime", "schedules[2].endTime");
                });
    }

    @Test
    void rejectsOverlappingWindowsOnTheSameDay() {
        assertThatThrownBy(() -> ScheduleInputValidator.toWindows(request(
                window(DayOfWeek.MONDAY, "08:00", "12:00"),
                window(DayOfWeek.MONDAY, "11:00", "15:00"))))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getFieldErrors()).extracting("field").containsExactly("schedules[1]");
                    assertThat(ex.getFieldErrors().get(0).message()).contains("schedules[0]").contains("MONDAY");
                });
    }

    @Test
    void rejectsDuplicateWindows() {
        assertThatThrownBy(() -> ScheduleInputValidator.toWindows(request(
                window(DayOfWeek.FRIDAY, "08:00", "12:00"),
                window(DayOfWeek.FRIDAY, "08:00", "12:00"))))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getFieldErrors()).extracting("field").containsExactly("schedules[1]"));
    }

    @Test
    void detectsOverlapRegardlessOfInputOrder() {
        assertThatThrownBy(() -> ScheduleInputValidator.toWindows(request(
                window(DayOfWeek.MONDAY, "13:00", "17:00"),
                window(DayOfWeek.MONDAY, "08:00", "14:00"))))
                .isInstanceOf(ApiException.class);
    }
}
