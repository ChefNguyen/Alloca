package com.alloca.booking.service;

import com.alloca.auth.domain.Role;
import com.alloca.auth.domain.User;
import com.alloca.auth.repository.UserRepository;
import com.alloca.auth.security.UserPrincipal;
import com.alloca.booking.domain.Booking;
import com.alloca.booking.dto.*;
import com.alloca.booking.repository.BookingRepository;
import com.alloca.booking.repository.BookingStatusHistoryRepository;
import com.alloca.common.dto.PageResponse;
import com.alloca.common.exception.ApiException;
import com.alloca.common.exception.ErrorCode;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BookingQueryService {
    private static final Set<String> SORT_FIELDS = Set.of("startTime", "endTime", "createdAt", "status", "id");
    private final BookingRepository bookings;
    private final BookingStatusHistoryRepository histories;
    private final UserRepository users;

    public BookingQueryService(BookingRepository bookings, BookingStatusHistoryRepository histories, UserRepository users) {
        this.bookings = bookings;
        this.histories = histories;
        this.users = users;
    }

    public PageResponse<BookingResponse> list(UserPrincipal principal, BookingSearchFilter filter, int page, int size, String sort) {
        User actor = actor(principal);
        if (page < 0) throw ApiException.invalidField("page", "must be at least 0");
        if (size < 1 || size > 100) throw ApiException.invalidField("size", "must be between 1 and 100");
        if (filter.from() != null && filter.to() != null && !filter.from().isBefore(filter.to())) {
            throw ApiException.invalidField("to", "must be after from");
        }
        String[] parts = (sort == null ? "startTime,desc" : sort).split(",", -1);
        if (parts.length != 2 || !SORT_FIELDS.contains(parts[0])
                || !(parts[1].equalsIgnoreCase("asc") || parts[1].equalsIgnoreCase("desc"))) {
            throw ApiException.invalidField("sort", "Use startTime, endTime, createdAt, status or id with asc/desc");
        }
        Sort ordering = Sort.by(Sort.Direction.fromString(parts[1]), parts[0]);
        if (!parts[0].equals("id")) ordering = ordering.and(Sort.by("id"));

        Specification<Booking> specification = (root, query, cb) -> {
            var owner = root.join("user", JoinType.INNER);
            // LEFT JOIN preserves self-service for owners without a department.
            var department = owner.join("department", JoinType.LEFT);
            List<Predicate> predicates = new ArrayList<>();
            if (actor.getRole() != Role.ADMIN) {
                Predicate own = cb.equal(owner.get("id"), actor.getId());
                predicates.add(actor.getRole() == Role.MANAGER && actor.getDepartment() != null
                        ? cb.or(own, cb.equal(department.get("id"), actor.getDepartment().getId())) : own);
            }
            if (filter.userId() != null) predicates.add(cb.equal(owner.get("id"), filter.userId()));
            if (filter.departmentId() != null) predicates.add(cb.equal(department.get("id"), filter.departmentId()));
            if (filter.resourceId() != null) predicates.add(cb.equal(root.get("resource").get("id"), filter.resourceId()));
            if (filter.status() != null) predicates.add(cb.equal(root.get("status"), filter.status()));
            if (filter.from() != null) predicates.add(cb.greaterThan(root.get("endTime"), filter.from()));
            if (filter.to() != null) predicates.add(cb.lessThan(root.get("startTime"), filter.to()));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return PageResponse.of(bookings.findAll(specification, PageRequest.of(page, size, ordering)), BookingResponse::from);
    }

    public BookingDetailResponse get(UUID id, UserPrincipal principal) {
        Booking booking = authorizedBooking(id, actor(principal));
        return new BookingDetailResponse(BookingResponse.from(booking), historyFor(booking.getId()));
    }

    public List<BookingStatusHistoryResponse> history(UUID id, UserPrincipal principal) {
        Booking booking = authorizedBooking(id, actor(principal));
        return historyFor(booking.getId());
    }

    private List<BookingStatusHistoryResponse> historyFor(UUID id) {
        return histories.findByBookingIdOrderByChangedAtAscIdAsc(id).stream()
                .map(BookingStatusHistoryResponse::from).toList();
    }

    private Booking authorizedBooking(UUID id, User actor) {
        if (id == null) throw ApiException.invalidField("bookingId", "must not be null");
        Booking booking = bookings.findForReadById(id)
                .orElseThrow(() -> ApiException.notFound(ErrorCode.BOOKING_NOT_FOUND, "Booking not found: " + id));
        User owner = booking.getUser();
        boolean sameDepartment = actor.getRole() == Role.MANAGER && actor.getDepartment() != null
                && owner.getDepartment() != null && actor.getDepartment().getId().equals(owner.getDepartment().getId());
        if (actor.getRole() != Role.ADMIN && !actor.getId().equals(owner.getId()) && !sameDepartment) {
            throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED, "Booking is outside your permitted scope");
        }
        return booking;
    }

    private User actor(UserPrincipal principal) {
        if (principal == null) throw new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.ACCESS_DENIED, "Authentication required");
        User actor = users.findById(principal.getId())
                .orElseThrow(() -> ApiException.notFound(ErrorCode.USER_NOT_FOUND, "User not found"));
        if (!actor.isActive()) throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED, "User account is inactive");
        return actor;
    }
}
