package com.alloca.booking.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.alloca.auth.domain.*;
import com.alloca.auth.repository.UserRepository;
import com.alloca.auth.security.UserPrincipal;
import com.alloca.booking.domain.*;
import com.alloca.booking.dto.*;
import com.alloca.booking.repository.*;
import com.alloca.common.exception.*;
import com.alloca.resource.domain.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class BookingQueryServiceTest {
    @Mock BookingRepository bookings;
    @Mock BookingStatusHistoryRepository histories;
    @Mock UserRepository users;
    private BookingQueryService service;
    private Department a;
    private User owner;
    private Booking booking;
    private static final BookingSearchFilter EMPTY = new BookingSearchFilter(null, null, null, null, null, null);

    @BeforeEach void setUp() {
        service = new BookingQueryService(bookings, histories, users);
        a = new Department(UUID.randomUUID(), "A");
        owner = user(Role.EMPLOYEE, a);
        Resource resource = new Resource(UUID.randomUUID(), ResourceStatus.AVAILABLE);
        booking = new Booking(UUID.randomUUID(), resource, owner, user(Role.ADMIN, null),
                Instant.parse("2030-06-10T02:00:00Z"), Instant.parse("2030-06-10T03:00:00Z"), null);
    }
    private User user(Role role, Department dept) {
        UUID id = UUID.randomUUID();
        return new User(id, id.toString(), id + "@test.invalid", "hash", "Name", role, dept);
    }
    private UserPrincipal principal(User actor) { return new UserPrincipal(actor.getId(), actor.getUsername(), actor.getRole(), actor.getDepartment() == null ? null : actor.getDepartment().getId()); }
    private UserPrincipal stubActor(User actor) {
        when(users.findById(actor.getId())).thenReturn(Optional.of(actor)); return principal(actor);
    }
    private void stubBooking() { when(bookings.findForReadById(booking.getId())).thenReturn(Optional.of(booking)); }

    @ParameterizedTest @EnumSource(Role.class)
    void allRolesCanReadOwnDetailsAndHistoryEvenWithoutDepartment(Role role) {
        owner.setRole(role); owner.setDepartment(null);
        UserPrincipal actor = stubActor(owner); stubBooking();
        var audit = new BookingStatusHistory(UUID.randomUUID(), booking, null, BookingStatus.CONFIRMED, null, "System", Instant.EPOCH);
        when(histories.findByBookingIdOrderByChangedAtAscIdAsc(booking.getId())).thenReturn(List.of(audit));
        var result = service.get(booking.getId(), actor);
        assertThat(result.booking().id()).isEqualTo(booking.getId());
        assertThat(result.statusHistory()).singleElement().satisfies(h -> {
            assertThat(h.changedBy()).isNull(); assertThat(h.changedAt()).isEqualTo(Instant.EPOCH);
        });
        assertThat(service.history(booking.getId(), actor)).hasSize(1);
    }

    @ParameterizedTest @EnumSource(value = Role.class, names = {"MANAGER", "ADMIN"})
    void authorizedRolesCanReadOtherOwner(Role role) {
        User actor = user(role, a); stubBooking();
        when(histories.findByBookingIdOrderByChangedAtAscIdAsc(booking.getId())).thenReturn(List.of());
        assertThat(service.get(booking.getId(), stubActor(actor)).booking().user().id()).isEqualTo(owner.getId());
    }

    @ParameterizedTest @CsvSource({"EMPLOYEE,false", "MANAGER,false", "MANAGER,true"})
    void outOfScopeCannotReadDetailOrHistory(Role role, boolean noDepartment) {
        User actor = user(role, noDepartment ? null : new Department(UUID.randomUUID(), "B"));
        UserPrincipal principal = stubActor(actor); stubBooking();
        assertThatThrownBy(() -> service.get(booking.getId(), principal)).isInstanceOfSatisfying(ApiException.class,
                error -> assertThat(error.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> service.history(booking.getId(), principal)).isInstanceOf(ApiException.class);
        verifyNoInteractions(histories);
    }

    @Test void creatorHasNoExtraAccessAfterOwnerMoves() {
        User creator = booking.getCreatedBy(); creator.setRole(Role.MANAGER); creator.setDepartment(a);
        owner.setDepartment(new Department(UUID.randomUUID(), "B")); stubBooking();
        assertThatThrownBy(() -> service.get(booking.getId(), stubActor(creator))).isInstanceOfSatisfying(ApiException.class,
                error -> assertThat(error.getCode()).isEqualTo(ErrorCode.ACCESS_DENIED));
        verifyNoInteractions(histories);
    }

    @Test void stalePrincipalCannotGrantAnOldAdminRole() {
        UserPrincipal staleAdmin = new UserPrincipal(owner.getId(), "old", Role.ADMIN, null);
        when(users.findById(owner.getId())).thenReturn(Optional.of(owner));
        booking.setUser(user(Role.EMPLOYEE, a)); stubBooking();
        assertThatThrownBy(() -> service.get(booking.getId(), staleAdmin)).isInstanceOf(ApiException.class);
    }

    @Test void listUsesDatabasePaginationAndStableSort() {
        UserPrincipal actor = stubActor(owner);
        when(bookings.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(booking), PageRequest.of(1, 10), 11));
        var result = service.list(actor, EMPTY, 1, 10, "startTime,desc");
        assertThat(result.page().totalElements()).isEqualTo(11);
        assertThat(result.items()).singleElement().extracting(BookingResponse::id).isEqualTo(booking.getId());
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(bookings).findAll(any(Specification.class), page.capture());
        assertThat(page.getValue().getSort().toString()).isEqualTo("startTime: DESC,id: ASC");
        verifyNoInteractions(histories);
    }

    @ParameterizedTest @CsvSource({"-1,10", "0,0", "0,101"})
    void rejectsInvalidPagination(int page, int size) {
        UserPrincipal actor = stubActor(owner);
        assertThatThrownBy(() -> service.list(actor, EMPTY, page, size, "startTime,desc")).isInstanceOf(ApiException.class);
        verifyNoInteractions(bookings, histories);
    }

    @ParameterizedTest @ValueSource(strings = {"user.passwordHash,asc", "startTime", "startTime,sideways", "startTime,asc,id", ""})
    void rejectsUnsupportedSort(String sort) {
        UserPrincipal actor = stubActor(owner);
        assertThatThrownBy(() -> service.list(actor, EMPTY, 0, 10, sort)).isInstanceOf(ApiException.class);
        verifyNoInteractions(bookings, histories);
    }

    @Test void rejectsEmptyOrReversedTimeRange() {
        UserPrincipal actor = stubActor(owner);
        for (Instant to : List.of(Instant.EPOCH, Instant.EPOCH.minusSeconds(1))) {
            assertThatThrownBy(() -> service.list(actor, new BookingSearchFilter(Instant.EPOCH, to, null, null, null, null), 0, 10, null))
                    .isInstanceOf(ApiException.class);
        }
        verifyNoInteractions(bookings);
    }

    @Test void missingRecordReturns404() {
        UserPrincipal actor = stubActor(owner);
        when(bookings.findForReadById(booking.getId())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(booking.getId(), actor)).isInstanceOfSatisfying(ApiException.class,
                error -> assertThat(error.getCode()).isEqualTo(ErrorCode.BOOKING_NOT_FOUND));
        verifyNoInteractions(histories);
    }

    @Test void rejectsUnauthenticatedMissingAndInactiveActors() {
        assertThatThrownBy(() -> service.get(booking.getId(), null)).isInstanceOf(ApiException.class);
        when(users.findById(owner.getId())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.history(booking.getId(), principal(owner))).isInstanceOf(ApiException.class);
        owner.deactivate(); when(users.findById(owner.getId())).thenReturn(Optional.of(owner));
        assertThatThrownBy(() -> service.list(principal(owner), EMPTY, 0, 10, null)).isInstanceOf(ApiException.class);
        verifyNoInteractions(bookings, histories);
    }
}
