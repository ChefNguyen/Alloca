package com.alloca.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.alloca.auth.domain.*;
import com.alloca.auth.repository.*;
import com.alloca.auth.security.*;
import com.alloca.booking.dto.BookingCreateRequest;
import com.alloca.booking.service.BookingService;
import com.alloca.resource.domain.*;
import com.alloca.resource.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@SpringBootTest(properties = {"spring.config.location=classpath:booking-integration-test.properties", "JWT_SECRET=booking-query-integration-test-only-secret-at-least-32-bytes"})
@AutoConfigureMockMvc
@Testcontainers
@Import(BookingQueryIT.FixedClock.class)
class BookingQueryIT {
    private static final Instant NOW = Instant.parse("2030-06-10T00:00:00Z");
    @Container @ServiceConnection static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    @TestConfiguration static class FixedClock {
        @Bean @Primary Clock queryTestClock() { return Clock.fixed(NOW, ZoneId.of("Asia/Ho_Chi_Minh")); }
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired BookingService service;
    @Autowired UserRepository users;
    @Autowired DepartmentRepository departments;
    @Autowired ResourceRepository resources;
    @Autowired ResourceScheduleRepository schedules;
    @Autowired ZoneRepository zones;
    @Autowired JwtService jwt;
    @Autowired JdbcTemplate jdbc;
    private Department a, b;
    private User a1, a2, b1, ma, admin, noDept;
    private Resource resource;
    private UUID bookingA1, bookingA2, bookingB, bookingNoDept;

    private User user(String name, Role role, Department department) {
        return users.save(new User(UUID.randomUUID(), name, name + "@test.invalid", "hash", name, role, department));
    }
    private UserPrincipal principal(User user) {
        return new UserPrincipal(user.getId(), user.getUsername(), user.getRole(), user.getDepartment() == null ? null : user.getDepartment().getId());
    }
    private BookingCreateRequest request(User owner, int hour) {
        return new BookingCreateRequest(resource.getId(), owner.getId(), NOW.plusSeconds((hour - 7) * 3600L), NOW.plusSeconds((hour - 6) * 3600L), "Scope test");
    }
    @BeforeEach void setUp() {
        jdbc.execute("truncate notifications, notification_outbox, booking_status_history, bookings, resource_schedules, resources, zones, users, departments cascade");
        a = departments.save(new Department(UUID.randomUUID(), "A")); b = departments.save(new Department(UUID.randomUUID(), "B"));
        a1 = user("a1", Role.EMPLOYEE, a); a2 = user("a2", Role.EMPLOYEE, a); b1 = user("b1", Role.EMPLOYEE, b);
        ma = user("ma", Role.MANAGER, a); admin = user("admin", Role.ADMIN, null); noDept = user("noDept", Role.MANAGER, null);
        var zone = zones.save(new Zone(UUID.randomUUID(), "North", "1", "Test"));
        resource = new Resource(UUID.randomUUID(), ResourceStatus.AVAILABLE); resource.setName("Department B room");
        resource.setType(ResourceType.ROOM); resource.setCapacity(10); resource.setZone(zone); resource.setDepartment(b);
        resource = resources.save(resource);
        schedules.save(new ResourceSchedule(UUID.randomUUID(), resource.getId(), DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(17, 0)));
        bookingA1 = service.createBooking(request(a1, 9), principal(a1)).id();
        bookingA2 = service.createBooking(request(a2, 10), principal(ma)).id();
        bookingB = service.createBooking(request(b1, 11), principal(admin)).id();
        bookingNoDept = service.createBooking(request(noDept, 12), principal(noDept)).id();
    }

    @Test void listAppliesOwnerScopeBeforePaginationAndCounts() throws Exception {
        mvc.perform(get("/api/v1/bookings").header("Authorization", "Bearer " + jwt.generateAccessToken(a1)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page.totalElements").value(1)).andExpect(jsonPath("$.items[0].id").value(bookingA1.toString()));
        mvc.perform(get("/api/v1/bookings").header("Authorization", "Bearer " + jwt.generateAccessToken(ma))
                .param("size", "1").param("sort", "startTime,asc")).andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2)).andExpect(jsonPath("$.page.totalPages").value(2))
                .andExpect(jsonPath("$.items[0].id").value(bookingA1.toString()));
        mvc.perform(get("/api/v1/bookings").header("Authorization", "Bearer " + jwt.generateAccessToken(ma))
                .param("size", "1").param("page", "1").param("sort", "startTime,asc"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(bookingA2.toString()));
        mvc.perform(get("/api/v1/bookings").header("Authorization", "Bearer " + jwt.generateAccessToken(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page.totalElements").value(4));
        mvc.perform(get("/api/v1/bookings").header("Authorization", "Bearer " + jwt.generateAccessToken(noDept)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page.totalElements").value(1)).andExpect(jsonPath("$.items[0].id").value(bookingNoDept.toString()));
    }

    @Test void departmentAndUserFiltersNeverExpandScope() throws Exception {
        for (User actor : List.of(a1, ma, noDept)) {
            mvc.perform(get("/api/v1/bookings").header("Authorization", "Bearer " + jwt.generateAccessToken(actor))
                    .param("userId", b1.getId().toString()).param("departmentId", b.getId().toString()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.page.totalElements").value(0)).andExpect(jsonPath("$.items").isEmpty());
        }
        mvc.perform(get("/api/v1/bookings").header("Authorization", "Bearer " + jwt.generateAccessToken(admin))
                .param("departmentId", a.getId().toString()).param("resourceId", resource.getId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page.totalElements").value(2));
    }

    @Test void detailAndHistoryProtectDirectIdsAndFollowCurrentDepartment() throws Exception {
        String managerToken = jwt.generateAccessToken(ma);
        mvc.perform(get("/api/v1/bookings/{id}", bookingA2).header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(bookingA2.toString()))
                .andExpect(jsonPath("$.statusHistory[0].changedBy.id").value(ma.getId().toString()));
        a2.setDepartment(b); users.save(a2);
        for (String suffix : List.of("", "/history")) {
            mvc.perform(get("/api/v1/bookings/" + bookingA2 + suffix).header("Authorization", "Bearer " + managerToken))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/api/v1/bookings/" + bookingB + suffix).header("Authorization", "Bearer " + jwt.generateAccessToken(a1)))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/v1/bookings").header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page.totalElements").value(1));
        mvc.perform(get("/api/v1/bookings/{id}/history", bookingA2).header("Authorization", "Bearer " + jwt.generateAccessToken(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].newStatus").value("CONFIRMED"));
    }

    @Test void filtersUseHalfOpenOverlapAndStatus() throws Exception {
        String adminToken = jwt.generateAccessToken(admin);
        mvc.perform(get("/api/v1/bookings").header("Authorization", "Bearer " + adminToken)
                .param("from", "2030-06-10T03:00:00Z").param("to", "2030-06-10T04:00:00Z"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].id").value(bookingA2.toString()));
        service.cancelBooking(bookingA1, null, principal(a1));
        mvc.perform(get("/api/v1/bookings").header("Authorization", "Bearer " + adminToken).param("status", "CANCELLED"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page.totalElements").value(1));
        mvc.perform(get("/api/v1/bookings/{id}/history", bookingA1).header("Authorization", "Bearer " + jwt.generateAccessToken(a1)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].newStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$[1].newStatus").value("CANCELLED"));
    }

    @Test void delegatedCreateChecksScopeAndNeverAcceptsForgedCreator() throws Exception {
        var input = request(b1, 14);
        mvc.perform(post("/api/v1/bookings").header("Authorization", "Bearer " + jwt.generateAccessToken(a1)).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(input)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/bookings").header("Authorization", "Bearer " + jwt.generateAccessToken(ma)).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(input)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/bookings").header("Authorization", "Bearer " + jwt.generateAccessToken(admin)).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(input)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.user.id").value(b1.getId().toString())).andExpect(jsonPath("$.createdBy.id").value(admin.getId().toString()));
        var forged = mapper.valueToTree(input); ((com.fasterxml.jackson.databind.node.ObjectNode) forged).put("createdBy", ma.getId().toString());
        mvc.perform(post("/api/v1/bookings").header("Authorization", "Bearer " + jwt.generateAccessToken(admin)).contentType(MediaType.APPLICATION_JSON).content(forged.toString()))
                .andExpect(status().isBadRequest());
    }

    @Test void inactiveDelegateIsRejectedAndHistorySurvivesResourceSoftDeletion() throws Exception {
        b1.deactivate(); users.save(b1);
        mvc.perform(post("/api/v1/bookings").header("Authorization", "Bearer " + jwt.generateAccessToken(admin)).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(request(b1, 14))))
                .andExpect(status().isForbidden());
        // Preserve historical rows even when their referenced resource has since been soft-deleted.
        jdbc.update("update resources set status='DELETED', deleted_at=? where id=?", java.sql.Timestamp.from(NOW), resource.getId());
        mvc.perform(get("/api/v1/bookings/{id}", bookingA1).header("Authorization", "Bearer " + jwt.generateAccessToken(a1)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.resource.status").value("DELETED"));
    }

    @ParameterizedTest @ValueSource(strings = {"page=-1", "size=101", "sort=user.passwordHash,asc", "from=2030-06-11T00:00:00Z&to=2030-06-10T00:00:00Z"})
    void invalidListInputsReturn400(String query) throws Exception {
        mvc.perform(get("/api/v1/bookings?" + query).header("Authorization", "Bearer " + jwt.generateAccessToken(admin)))
                .andExpect(status().isBadRequest());
    }
}
