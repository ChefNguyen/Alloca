package com.alloca.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.alloca.auth.domain.*;
import com.alloca.auth.repository.*;
import com.alloca.auth.security.JwtService;
import com.alloca.auth.security.UserPrincipal;
import com.alloca.booking.dto.*;
import com.alloca.booking.repository.BookingRepository;
import com.alloca.booking.service.BookingService;
import com.alloca.common.exception.ApiException;
import com.alloca.common.exception.ErrorCode;
import com.alloca.notification.service.NotificationService;
import com.alloca.resource.domain.*;
import com.alloca.resource.dto.ResourceScheduleReplaceRequest;
import com.alloca.resource.dto.ResourceStatusUpdateRequest;
import com.alloca.resource.repository.*;
import com.alloca.resource.service.ResourceScheduleService;
import com.alloca.resource.service.ResourceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.function.IntFunction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** PostgreSQL row-lock and atomicity acceptance tests. No application database is used. */
@SpringBootTest(properties = {
        "spring.config.location=classpath:booking-integration-test.properties", "JWT_SECRET=booking-integration-test-secret-at-least-32-bytes",
        "spring.datasource.hikari.maximum-pool-size=24", "logging.level.root=WARN"})
@AutoConfigureMockMvc
@Testcontainers
@Import(BookingConcurrencyIT.TestClockConfiguration.class)
@Timeout(90)
class BookingConcurrencyIT {
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Instant NOW = Instant.parse("2030-06-10T00:00:00Z");
    private static final Instant START = NOW.plusSeconds(7200);
    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @TestConfiguration
    static class TestClockConfiguration {
        @Bean @Primary MutableClock bookingTestClock() { return new MutableClock(); }
    }

    static final class MutableClock extends Clock {
        private final AtomicReference<Instant> value = new AtomicReference<>(NOW);
        void set(Instant now) { value.set(now); }
        @Override public ZoneId getZone() { return ZONE; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant(), zone); }
        @Override public Instant instant() { return value.get(); }
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired BookingService service;
    @Autowired ResourceService resourceService;
    @Autowired ResourceScheduleService scheduleService;
    @Autowired NotificationService notificationService;
    @Autowired BookingRepository bookings;
    @Autowired ResourceRepository resources;
    @Autowired ResourceScheduleRepository schedules;
    @Autowired UserRepository users;
    @Autowired DepartmentRepository departments;
    @Autowired ZoneRepository zones;
    @Autowired JwtService jwt;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired MutableClock clock;
    private TransactionTemplate tx;
    private User owner;
    private User manager;
    private User admin;
    private Resource resource;
    private String ownerToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        clock.set(NOW);
        tx = new TransactionTemplate(transactionManager);
        // This database belongs exclusively to this Testcontainer.
        jdbc.execute("truncate notifications, notification_outbox, booking_status_history, bookings, resource_schedules, resources, zones, users, departments cascade");
        Department a = departments.save(new Department(UUID.randomUUID(), "A"));
        Department b = departments.save(new Department(UUID.randomUUID(), "B"));
        owner = users.save(user("owner", Role.EMPLOYEE, a));
        manager = users.save(user("manager", Role.MANAGER, a));
        admin = users.save(user("admin", Role.ADMIN, b));
        Zone zone = zones.save(new Zone(UUID.randomUUID(), "North", "1", "Test"));
        resource = new Resource(UUID.randomUUID(), ResourceStatus.AVAILABLE);
        resource.setName("Test room");
        resource.setType(ResourceType.ROOM);
        resource.setCapacity(10);
        resource.setZone(zone);
        resource.setDepartment(b); // Resource ownership does not restrict booking.
        resource = resources.save(resource);
        schedules.save(new ResourceSchedule(UUID.randomUUID(), resource.getId(), DayOfWeek.MONDAY,
                LocalTime.of(8, 0), LocalTime.of(17, 0)));
        ownerToken = jwt.generateAccessToken(owner);
        adminToken = jwt.generateAccessToken(admin);
    }

    private User user(String name, Role role, Department department) {
        return new User(UUID.randomUUID(), name, name + "@test.invalid", "unused-test-hash", name, role, department);
    }

    private UserPrincipal principal(User user) {
        return new UserPrincipal(user.getId(), user.getUsername(), user.getRole(),
                user.getDepartment() == null ? null : user.getDepartment().getId());
    }

    private BookingCreateRequest request(Instant start) {
        return new BookingCreateRequest(resource.getId(), null, start, start.plusSeconds(3600), "Integration test");
    }

    private int createHttp(Instant start) throws Exception {
        return mvc.perform(post("/api/v1/bookings").header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(request(start))))
                .andReturn().getResponse().getStatus();
    }

    private int cancelHttp(UUID id, String token) throws Exception {
        return mvc.perform(post("/api/v1/bookings/{id}/cancel", id).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Changed plans\"}"))
                .andReturn().getResponse().getStatus();
    }

    private long count(String table) { return jdbc.queryForObject("select count(*) from " + table, Long.class); }

    private <T> List<T> concurrently(int total, IntFunction<Callable<T>> operation) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(total);
        CountDownLatch ready = new CountDownLatch(total);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < total; i++) {
                Callable<T> task = operation.apply(i);
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(15, TimeUnit.SECONDS)) throw new AssertionError("Start barrier timed out");
                    return task.call();
                }));
            }
            assertThat(ready.await(15, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) results.add(future.get(45, TimeUnit.SECONDS));
            return results;
        } finally {
            start.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void twentyOverlappingRequestsCreateExactlyOneBookingHistoryAndConfirmation() throws Exception {
        List<Integer> statuses = concurrently(20, i -> () -> createHttp(START));
        assertThat(statuses).filteredOn(code -> code == 201).hasSize(1);
        assertThat(statuses).filteredOn(code -> code == 409).hasSize(19);
        assertThat(count("bookings")).isEqualTo(1);
        assertThat(count("booking_status_history")).isEqualTo(1);
        assertThat(count("notifications")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select event_type from notifications", String.class)).isEqualTo("BOOKING_CONFIRMED");
        assertThat(jdbc.queryForObject("select user_id from notifications", UUID.class)).isEqualTo(owner.getId());
    }

    @Test
    void adjacentIntervalsBothSucceed() throws Exception {
        assertThat(concurrently(2, i -> () -> createHttp(START.plusSeconds(i * 3600L))))
                .containsExactlyInAnyOrder(201, 201);
        assertThat(count("bookings")).isEqualTo(2);
        assertThat(count("booking_status_history")).isEqualTo(2);
        assertThat(count("notifications")).isEqualTo(2);
    }

    @Test
    void failureAfterPersistingAllCreateEffectsRollsBackEverything() {
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            service.createBooking(request(START), principal(owner));
            assertThat(count("bookings")).isEqualTo(1);
            assertThat(count("booking_status_history")).isEqualTo(1);
            assertThat(count("notifications")).isEqualTo(1);
            throw new IllegalStateException("Injected before commit");
        })).isInstanceOf(IllegalStateException.class).hasMessage("Injected before commit");
        assertThat(count("bookings")).isZero();
        assertThat(count("booking_status_history")).isZero();
        assertThat(count("notifications")).isZero();
    }

    @Test
    void concurrentCancellationTransitionsOnlyOnceAndNotifiesOwnerAndOriginalCreator() throws Exception {
        BookingResponse created = service.createBooking(new BookingCreateRequest(resource.getId(), owner.getId(),
                START, START.plusSeconds(3600), null), principal(manager));
        List<Integer> statuses = concurrently(10, i -> () -> cancelHttp(created.id(), adminToken));
        assertThat(statuses).filteredOn(code -> code == 204).hasSize(1);
        assertThat(statuses).filteredOn(code -> code == 409).hasSize(9);
        assertThat(count("booking_status_history")).isEqualTo(2);
        assertThat(jdbc.queryForList("select user_id from notifications where event_type='BOOKING_CANCELLED'", UUID.class))
                .containsExactlyInAnyOrder(owner.getId(), manager.getId());
        assertThat(jdbc.queryForObject("select cancelled_by from bookings", UUID.class)).isEqualTo(admin.getId());
        assertThat(jdbc.queryForObject("select cancel_reason from bookings", String.class)).isEqualTo("Changed plans");
        assertThat(createHttp(START)).isEqualTo(201);
        assertThat(count("bookings")).isEqualTo(2);
    }

    @Test
    void cancellationRollbackPreservesConfirmedStateAndOriginalAudit() {
        UUID id = service.createBooking(request(START), principal(owner)).id();
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            service.cancelBooking(id, null, principal(owner));
            assertThat(jdbc.queryForObject("select status from bookings", String.class)).isEqualTo("CANCELLED");
            assertThat(count("notifications")).isEqualTo(2);
            throw new IllegalStateException("Injected cancellation failure");
        })).isInstanceOf(IllegalStateException.class).hasMessage("Injected cancellation failure");
        assertThat(jdbc.queryForObject("select status from bookings", String.class)).isEqualTo("CONFIRMED");
        assertThat(jdbc.queryForObject("select cancelled_by from bookings", UUID.class)).isNull();
        assertThat(count("booking_status_history")).isEqualTo(1);
        assertThat(count("notifications")).isEqualTo(1);
    }

    @Test
    void notificationReprocessingIsDeduplicatedAfterCommit() throws Exception {
        UUID id = service.createBooking(request(START), principal(owner)).id();
        assertThat(concurrently(2, i -> () -> tx.execute(status -> {
            notificationService.confirmBooking(bookings.findById(id).orElseThrow(), NOW);
            return true;
        }))).containsOnly(true);
        assertThat(count("notifications")).isEqualTo(1);
    }

    /** Holds the real row lock until PostgreSQL confirms the other transaction is waiting on it. */
    private <T> T whileResourceLocked(Runnable holderMutation, Callable<T> contender) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Future<?> holder = pool.submit(() -> tx.executeWithoutResult(status -> {
            resources.findByIdForUpdate(resource.getId()).orElseThrow();
            holderMutation.run();
            locked.countDown();
            try {
                if (!release.await(25, TimeUnit.SECONDS)) throw new AssertionError("Lock release timed out");
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
        }));
        try {
            assertThat(locked.await(15, TimeUnit.SECONDS)).isTrue();
            Future<T> waiting = pool.submit(contender);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
            boolean observedWait = false;
            while (System.nanoTime() < deadline && !waiting.isDone()) {
                Long locks = jdbc.queryForObject("select count(*) from pg_stat_activity where datname = current_database() and wait_event_type = 'Lock' and query like '%resources%'", Long.class);
                if (locks != null && locks > 0) { observedWait = true; break; }
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
            }
            assertThat(observedWait).as("PostgreSQL must block the competing resource transaction").isTrue();
            release.countDown();
            holder.get(10, TimeUnit.SECONDS);
            return waiting.get(20, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void creationWaitsForSuspendAndRevalidatesCommittedStatus() throws Exception {
        int code = whileResourceLocked(() -> resourceService.updateStatus(resource.getId(),
                new ResourceStatusUpdateRequest(ResourceStatus.SUSPENDED, null)), () -> createHttp(START));
        assertThat(code).isEqualTo(400);
        assertThat(count("bookings")).isZero();
        assertThat(count("notifications")).isZero();
    }

    private ResourceScheduleReplaceRequest afternoonOnly() {
        return new ResourceScheduleReplaceRequest(List.of(
                new ResourceScheduleReplaceRequest.Window(DayOfWeek.MONDAY, "13:00", "17:00")));
    }

    @Test
    void creationWaitsForScheduleReplacementAndRevalidatesNewHours() throws Exception {
        int code = whileResourceLocked(() -> scheduleService.replace(resource.getId(), afternoonOnly()), () -> createHttp(START));
        assertThat(code).isEqualTo(400);
        assertThat(count("bookings")).isZero();
    }

    @Test
    void scheduleReplacementWaitsForCreationThenRejectsInvalidatingIt() throws Exception {
        ErrorCode code = whileResourceLocked(() -> service.createBooking(request(START), principal(owner)), () -> {
            try { scheduleService.replace(resource.getId(), afternoonOnly()); return null; }
            catch (ApiException error) { return error.getCode(); }
        });
        assertThat(code).isEqualTo(ErrorCode.RESOURCE_SCHEDULE_CONFLICTS_WITH_BOOKINGS);
        assertThat(count("bookings")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select start_time::text from resource_schedules", String.class))
                .isEqualTo("08:00:00");
    }

    @Test
    void creationWaitsForDeletionAndSeesDeletedResource() throws Exception {
        int code = whileResourceLocked(() -> {
            resourceService.updateStatus(resource.getId(), new ResourceStatusUpdateRequest(ResourceStatus.SUSPENDED, null));
            resourceService.delete(resource.getId());
        }, () -> createHttp(START));
        assertThat(code).isEqualTo(404);
        assertThat(count("bookings")).isZero();
    }

    @Test
    void deletionWaitsForCreationThenCannotRemoveResourceWithBooking() throws Exception {
        ErrorCode code = whileResourceLocked(() -> service.createBooking(request(START), principal(owner)), () -> {
            resourceService.updateStatus(resource.getId(), new ResourceStatusUpdateRequest(ResourceStatus.SUSPENDED, null));
            try { resourceService.delete(resource.getId()); return null; }
            catch (ApiException error) { return error.getCode(); }
        });
        assertThat(code).isEqualTo(ErrorCode.RESOURCE_HAS_ONGOING_OR_FUTURE_BOOKINGS);
        assertThat(resources.findById(resource.getId()).orElseThrow().getStatus()).isEqualTo(ResourceStatus.SUSPENDED);
        assertThat(count("bookings")).isEqualTo(1);
    }

    @Test
    void cancellationRechecksClockAfterWaitingForLock() throws Exception {
        UUID id = service.createBooking(request(START), principal(owner)).id();
        int code = whileResourceLocked(() -> clock.set(START), () -> cancelHttp(id, ownerToken));
        assertThat(code).isEqualTo(409);
        assertThat(count("booking_status_history")).isEqualTo(1);
        assertThat(count("notifications")).isEqualTo(1);
    }

    @Test
    void managerCannotUseDirectIdOrDepartmentParameterAfterOwnerMoves() throws Exception {
        UUID id = service.createBooking(new BookingCreateRequest(resource.getId(), owner.getId(), START,
                START.plusSeconds(3600), null), principal(manager)).id();
        owner.setDepartment(admin.getDepartment());
        users.save(owner);
        int code = mvc.perform(post("/api/v1/bookings/{id}/cancel", id)
                .header("Authorization", "Bearer " + jwt.generateAccessToken(manager))
                .param("departmentId", admin.getDepartment().getId().toString())).andReturn().getResponse().getStatus();
        assertThat(code).isEqualTo(403);
        assertThat(count("booking_status_history")).isEqualTo(1);
        assertThat(count("notifications")).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"UTC", "Asia/Ho_Chi_Minh", "America/New_York"})
    void localOperatingHoursAndUtcBookingInstantsSurviveServerTimezoneChanges(String serverZone) {
        TimeZone previous = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(serverZone));
            schedules.deleteAll();
            schedules.save(new ResourceSchedule(UUID.randomUUID(), resource.getId(), DayOfWeek.MONDAY,
                    LocalTime.of(8, 15), LocalTime.of(17, 45)));
            assertThat(jdbc.queryForObject("select start_time::text from resource_schedules", String.class)).isEqualTo("08:15:00");
            assertThat(jdbc.queryForObject("select end_time::text from resource_schedules", String.class)).isEqualTo("17:45:00");
            assertThat(schedules.findByResourceIdAndDayOfWeekOrderByStartTimeAsc(resource.getId(), (short) 1))
                    .singleElement().satisfies(window -> {
                        assertThat(window.getStartTime()).isEqualTo(LocalTime.of(8, 15));
                        assertThat(window.getEndTime()).isEqualTo(LocalTime.of(17, 45));
                    });
            service.createBooking(request(START), principal(owner));
            assertThat(jdbc.queryForObject("select (start_time at time zone 'UTC')::text from bookings", String.class))
                    .isEqualTo("2030-06-10 02:00:00");
            assertThat(jdbc.queryForObject("select (changed_at at time zone 'UTC')::text from booking_status_history", String.class))
                    .isEqualTo("2030-06-10 00:00:00");
        } finally {
            TimeZone.setDefault(previous);
        }
    }
}
