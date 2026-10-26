package com.alloca.resource.service;

import com.alloca.common.api.ApiException;
import com.alloca.common.api.ErrorCode;
import com.alloca.common.api.ErrorResponse;
import com.alloca.resource.domain.ScheduleWindow;
import com.alloca.resource.dto.ResourceScheduleReplaceRequest;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * Turns a replace request into windows, rejecting what FR-RES-02 forbids: {@code start >= end} and
 * overlapping windows on the same day. Touching windows are fine. Errors point at
 * {@code schedules[i]} so a form can mark the offending row.
 */
final class ScheduleInputValidator {

    private ScheduleInputValidator() {
    }

    static List<ScheduleWindow> toWindows(ResourceScheduleReplaceRequest request) {
        List<ErrorResponse.FieldError> errors = new ArrayList<>();
        List<IndexedWindow> indexed = new ArrayList<>();

        for (int i = 0; i < request.schedules().size(); i++) {
            ResourceScheduleReplaceRequest.Window input = request.schedules().get(i);
            LocalTime start = LocalTime.parse(input.startTime());
            LocalTime end = LocalTime.parse(input.endTime());
            if (!start.isBefore(end)) {
                errors.add(new ErrorResponse.FieldError(
                        "schedules[%d].endTime".formatted(i), "must be later than startTime"));
                continue;
            }
            indexed.add(new IndexedWindow(i, new ScheduleWindow(input.dayOfWeek(), start, end)));
        }

        for (DayOfWeek day : DayOfWeek.values()) {
            List<IndexedWindow> sameDay = indexed.stream()
                    .filter(entry -> entry.window().day() == day)
                    .sorted(Comparator.comparing((IndexedWindow entry) -> entry.window().start())
                            .thenComparing(entry -> entry.window().end()))
                    .toList();
            for (int i = 1; i < sameDay.size(); i++) {
                IndexedWindow previous = sameDay.get(i - 1);
                IndexedWindow current = sameDay.get(i);
                if (current.window().start().isBefore(previous.window().end())) {
                    errors.add(new ErrorResponse.FieldError(
                            "schedules[%d]".formatted(current.index()),
                            "overlaps schedules[%d] on %s".formatted(previous.index(), day)));
                }
            }
        }

        if (!errors.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                    "Request validation failed", errors, null);
        }
        return indexed.stream().map(IndexedWindow::window).toList();
    }

    private record IndexedWindow(int index, ScheduleWindow window) {
    }
}
