package com.alloca.booking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.alloca.auth.domain.Department;
import com.alloca.auth.domain.Role;
import com.alloca.auth.domain.User;
import com.alloca.auth.repository.UserRepository;
import com.alloca.auth.security.UserPrincipal;
import com.alloca.booking.domain.Booking;
import com.alloca.booking.domain.BookingStatus;
import com.alloca.booking.domain.BookingStatusHistory;
import com.alloca.booking.dto.BookingCancelRequest;
import com.alloca.booking.repository.BookingRepository;
import com.alloca.booking.repository.BookingStatusHistoryRepository;
import com.alloca.common.exception.ApiException;
import com.alloca.common.exception.ErrorCode;
import com.alloca.notification.service.NotificationService;
import com.alloca.resource.domain.Resource;
import com.alloca.resource.domain.ResourceStatus;
import com.alloca.resource.repository.ResourceRepository;
import com.alloca.resource.repository.ResourceScheduleRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class BookingCancellationServiceTest {
    private static final Instant NOW = Instant.parse("2030-06-10T00:00:00Z");
    @Mock ResourceRepository resources;
    @Mock ResourceScheduleRepository schedules;
    @Mock BookingRepository bookings;
    @Mock BookingStatusHistoryRepository histories;
    @Mock UserRepository users;
    @Mock NotificationService notifications;
    @Mock Clock clock;
    private BookingService service;
    private Department department;
    private User owner;
    private User creator;
    private Booking booking;

    @BeforeEach
    void setUp() {
        service = new BookingService(resources, schedules, bookings, histories, users, notifications,
                clock, ZoneId.of("Asia/Ho_Chi_Minh"));
        department = new Department(UUID.randomUUID(), "A");
        owner = user(Role.EMPLOYEE, department);
        creator = user(Role.MANAGER, department);
        booking = new Booking(UUID.randomUUID(), new Resource(UUID.randomUUID(), ResourceStatus.AVAILABLE),
                owner, creator, NOW.plusSeconds(7200), NOW.plusSeconds(10800), "Meeting", NOW.minusSeconds(60));
    }

    private User user(Role role, Department dept) {
        UUID id = UUID.randomUUID();
        return new User(id, "u" + id, id + "@test.invalid", "hash", "Test", role, dept);
    }

    private UserPrincipal principal(User user) {
        return new UserPrincipal(user.getId(), user.getUsername(), user.getRole(),
                user.getDepartment() == null ? null : user.getDepartment().getId());
    }

    private void stubLocked(User actor) {
        when(bookings.findResourceIdById(booking.getId())).thenReturn(Optional.of(booking.getResource().getId()));
        when(resources.findByIdForUpdate(booking.getResource().getId())).thenReturn(Optional.of(booking.getResource()));
        when(bookings.findByIdForUpdate(booking.getId())).thenReturn(Optional.of(booking));
        when(users.findById(actor.getId())).thenReturn(Optional.of(actor));
    }

    private void assertError(Runnable call, HttpStatus status, ErrorCode code) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class, error -> {
            assertThat(error.getStatus()).isEqualTo(status);
            assertThat(error.getCode()).isEqualTo(code);
        });
        verify(bookings, never()).save(any());
        verifyNoInteractions(histories, notifications);
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void everyRoleCanCancelOwnBookingIncludingWithoutDepartment(Role role) {
        owner.setRole(role);
        owner.setDepartment(null);
        stubLocked(owner);
        when(clock.instant()).thenReturn(NOW);
        service.cancelBooking(booking.getId(), null, principal(owner));
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(booking.getCancelReason()).isNull();
        assertThat(booking.getCancelledBy()).isSameAs(owner);
        verify(notifications).cancelBooking(booking, NOW);
    }

    @Test
    void managerCancelsOwnerInCurrentDepartmentAndRecordsAuditInLockOrder() {
        stubLocked(creator);
        when(clock.instant()).thenReturn(NOW);
        service.cancelBooking(booking.getId(), new BookingCancelRequest("x".repeat(500)), principal(creator));
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(booking.getCancelledBy()).isSameAs(creator);
        assertThat(booking.getUpdatedAt()).isEqualTo(NOW);
        ArgumentCaptor<BookingStatusHistory> audit = ArgumentCaptor.forClass(BookingStatusHistory.class);
        verify(histories).save(audit.capture());
        assertThat(audit.getValue().getOldStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(audit.getValue().getNewStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(audit.getValue().getChangedBy()).isSameAs(creator);
        assertThat(audit.getValue().getChangedAt()).isEqualTo(NOW);
        assertThat(audit.getValue().getReason()).hasSize(500);
        var order = inOrder(resources, bookings, clock, histories, notifications);
        order.verify(bookings).findResourceIdById(booking.getId());
        order.verify(resources).findByIdForUpdate(booking.getResource().getId());
        order.verify(bookings).findByIdForUpdate(booking.getId());
        order.verify(clock).instant();
        order.verify(bookings).save(booking);
        order.verify(histories).save(any());
        order.verify(notifications).cancelBooking(booking, NOW);
    }

    @Test
    void adminCanCancelOtherDepartmentOnSuspendedResource() {
        User admin = user(Role.ADMIN, new Department(UUID.randomUUID(), "B"));
        booking.getResource().changeStatus(ResourceStatus.SUSPENDED);
        stubLocked(admin);
        when(clock.instant()).thenReturn(NOW);
        service.cancelBooking(booking.getId(), null, principal(admin));
        assertThat(booking.getCancelledBy()).isSameAs(admin);
    }

    @ParameterizedTest
    @CsvSource({"EMPLOYEE,false", "MANAGER,false", "MANAGER,true"})
    void deniesOtherEmployeeAndOutOfScopeOrDepartmentlessManager(Role role, boolean noDepartment) {
        User actor = user(role, noDepartment ? null : new Department(UUID.randomUUID(), "B"));
        stubLocked(actor);
        assertError(() -> service.cancelBooking(booking.getId(), null, principal(actor)),
                HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void oldCreatorLosesScopeWhenOwnerMovesDepartments() {
        owner.setDepartment(new Department(UUID.randomUUID(), "B"));
        stubLocked(creator);
        assertError(() -> service.cancelBooking(booking.getId(), null, principal(creator)),
                HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED);
    }

    @Test
    void usesCurrentActorRoleAndDepartmentInsteadOfStalePrincipal() {
        UserPrincipal staleAdmin = new UserPrincipal(creator.getId(), "old", Role.ADMIN, department.getId());
        creator.setRole(Role.MANAGER);
        creator.setDepartment(new Department(UUID.randomUUID(), "B"));
        stubLocked(creator);
        assertError(() -> service.cancelBooking(booking.getId(), null, staleAdmin),
                HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED);
    }

    @ParameterizedTest
    @CsvSource({"EMPLOYEE,0", "EMPLOYEE,1", "MANAGER,0", "MANAGER,1", "ADMIN,0", "ADMIN,1"})
    void noRoleCanCancelAtOrAfterStart(Role role, long secondsAfterStart) {
        User actor = role == Role.EMPLOYEE ? owner : user(role, department);
        stubLocked(actor);
        when(clock.instant()).thenReturn(booking.getStartTime().plusSeconds(secondsAfterStart));
        assertError(() -> service.cancelBooking(booking.getId(), null, principal(actor)),
                HttpStatus.CONFLICT, ErrorCode.BOOKING_CANNOT_BE_CANCELLED);
    }

    @ParameterizedTest
    @EnumSource(value = BookingStatus.class, names = {"CANCELLED", "COMPLETED"})
    void terminalStatusHasNoRepeatedSideEffects(BookingStatus status) {
        booking.setStatus(status);
        stubLocked(owner);
        when(clock.instant()).thenReturn(NOW);
        assertError(() -> service.cancelBooking(booking.getId(), null, principal(owner)),
                HttpStatus.CONFLICT, ErrorCode.BOOKING_CANNOT_BE_CANCELLED);
        assertThat(booking.getStatus()).isEqualTo(status);
    }

    @Test
    void rejectsInactiveActor() {
        owner.deactivate();
        stubLocked(owner);
        assertError(() -> service.cancelBooking(booking.getId(), null, principal(owner)),
                HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED);
    }

    @Test
    void missingBookingReturns404() {
        when(bookings.findResourceIdById(booking.getId())).thenReturn(Optional.empty());
        assertError(() -> service.cancelBooking(booking.getId(), null, principal(owner)),
                HttpStatus.NOT_FOUND, ErrorCode.BOOKING_NOT_FOUND);
        verifyNoInteractions(resources, users);
    }

    @Test
    void validatesDirectServiceInputsBeforeLookingUpRecords() {
        assertError(() -> service.cancelBooking(booking.getId(), null, null),
                HttpStatus.UNAUTHORIZED, ErrorCode.ACCESS_DENIED);
        assertError(() -> service.cancelBooking(null, null, principal(owner)),
                HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR);
        assertError(() -> service.cancelBooking(booking.getId(), new BookingCancelRequest("x".repeat(501)), principal(owner)),
                HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR);
        verifyNoInteractions(resources, users);
    }
}
