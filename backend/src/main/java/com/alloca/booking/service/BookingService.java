package com.alloca.booking.service;

import com.alloca.auth.domain.User;
import com.alloca.auth.domain.Role;
import com.alloca.auth.repository.UserRepository;
import com.alloca.auth.security.UserPrincipal;
import com.alloca.booking.domain.AvailabilityCalculator;
import com.alloca.booking.domain.Booking;
import com.alloca.booking.domain.BookingStatus;
import com.alloca.booking.domain.BookingStatusHistory;
import com.alloca.booking.domain.BookingTimeRules;
import com.alloca.booking.domain.TimeSlot;
import com.alloca.booking.dto.BookingCreateRequest;
import com.alloca.booking.dto.BookingCancelRequest;
import com.alloca.booking.dto.BookingResponse;
import com.alloca.booking.dto.ResourceAvailabilityResponse;
import com.alloca.booking.dto.TimeSlotResponse;
import com.alloca.booking.repository.BookingRepository;
import com.alloca.booking.repository.BookingStatusHistoryRepository;
import com.alloca.common.exception.ApiException;
import com.alloca.common.exception.ErrorCode;
import com.alloca.notification.service.NotificationService;
import com.alloca.resource.domain.OperatingSchedule;
import com.alloca.resource.domain.ScheduleWindow;
import com.alloca.resource.domain.Resource;
import com.alloca.resource.domain.ResourceSchedule;
import com.alloca.resource.domain.ResourceStatus;
import com.alloca.resource.repository.ResourceRepository;
import com.alloca.resource.repository.ResourceScheduleRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.github.f4b6a3.uuid.UuidCreator;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

/**
 * Application service for booking workflows.
 */
@Service
@Transactional(readOnly = true) // mọi public method trong class đều được bọc transaction
public class BookingService {

    private static final Duration SLOT_DURATION = Duration.ofMinutes(30);

    private final ResourceRepository resourceRepository;
    private final ResourceScheduleRepository scheduleRepository;
    private final BookingRepository bookingRepository;
    private final BookingStatusHistoryRepository bookingStatusHistoryRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final Clock clock;
    private final ZoneId businessZone;

    public BookingService(
            ResourceRepository resourceRepository,
            ResourceScheduleRepository scheduleRepository,
            BookingRepository bookingRepository,
            BookingStatusHistoryRepository bookingStatusHistoryRepository,
            UserRepository userRepository,
            NotificationService notificationService,
            Clock clock,
            ZoneId businessZone
    ) {
        this.resourceRepository = resourceRepository;
        this.scheduleRepository = scheduleRepository;
        this.bookingRepository = bookingRepository;
        this.bookingStatusHistoryRepository = bookingStatusHistoryRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.clock = clock;
        this.businessZone = businessZone;
    }

    /**
     * ALC-34: Tính toán các khung giờ trống của tài nguyên theo ngày.
     *
     * <p>Quy tắc:
     * - Chỉ hỗ trợ bước phân mảnh 30 phút.
     * - Resource DELETED hoặc không tồn tại -> 404 Not Found.
     * - Resource SUSPENDED hoặc ngoài chân trời 30 ngày hoặc quá khứ -> availableSlots rỗng.
     * - Snap operating window theo lưới 30 phút để đảm bảo các slot hợp lệ.
     * - Loại bỏ các slot đã qua nếu ngày truy vấn là ngày hiện tại.
     */
    public ResourceAvailabilityResponse getResourceAvailability(UUID resourceId, LocalDate date, Integer slotMinutes) {
        // Check Input Valdation
        if (resourceId == null) {
            throw ApiException.invalidField("resourceId", "must not be null");
        }
        if (date == null) {
            throw ApiException.invalidField("date", "must not be null");
        }
        if (slotMinutes != null && slotMinutes != 30) {
            throw ApiException.invalidField("slotMinutes", "Only 30-minute slot increments are supported");
        }

        Resource resource = resourceRepository.findById(resourceId)
                .orElseThrow(() -> ApiException.notFound(ErrorCode.RESOURCE_NOT_FOUND, "Resource not found: " + resourceId));

        if (resource.getStatus() == ResourceStatus.DELETED) {
            throw ApiException.notFound(ErrorCode.RESOURCE_NOT_FOUND, "Resource not found: " + resourceId);
        }

        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, businessZone);
        LocalDate maxHorizon = today.plusDays(30);

        // Lấy lịch hoạt động của ngày trong tuần (1 = Thứ Hai, 7 = Chủ Nhật)
        short dayOfWeek = (short) date.getDayOfWeek().getValue();
        List<ResourceSchedule> schedules = scheduleRepository
                .findByResourceIdAndDayOfWeekOrderByStartTimeAsc(resourceId, dayOfWeek);

        List<TimeSlot> rawOperatingWindows = schedules.stream()
                .map(s -> new TimeSlot(
                        date.atTime(s.getStartTime()).atZone(businessZone).toInstant(),
                        date.atTime(s.getEndTime()).atZone(businessZone).toInstant()
                ))
                .toList();

        List<TimeSlotResponse> operatingWindowResponses = rawOperatingWindows.stream()
                .map(w -> new TimeSlotResponse(w.startTime(), w.endTime()))
                .toList();

        // Nếu resource SUSPENDED, hoặc ngày xem trong quá khứ, hoặc vượt quá 30 ngày, hoặc ngày đó đóng cửa
        if (resource.getStatus() == ResourceStatus.SUSPENDED
                || date.isBefore(today)
                || date.isAfter(maxHorizon)
                || rawOperatingWindows.isEmpty()) {
            return new ResourceAvailabilityResponse(
                    resourceId,
                    date,
                    businessZone.getId(),
                    resource.getStatus(),
                    operatingWindowResponses,
                    List.of()
            );
        }

        // 1. Chia các operating windows thành các ứng viên slot 30 phút (snap theo lưới)
        OperatingSchedule operatingSchedule = new OperatingSchedule(schedules.stream()
                .map(s -> new ScheduleWindow(date.getDayOfWeek(), s.getStartTime(), s.getEndTime()))
                .toList(), businessZone);
        List<TimeSlot> candidateSlots = new ArrayList<>();
        for (ScheduleWindow merged : operatingSchedule.mergedWindows(date.getDayOfWeek())) {
            TimeSlot window = new TimeSlot(date.atTime(merged.start()).atZone(businessZone).toInstant(),
                    date.atTime(merged.end()).atZone(businessZone).toInstant());
            Instant snappedStart = BookingTimeRules.snapForwardToGrid(window.startTime(), businessZone);
            if (snappedStart.isBefore(window.endTime())) {
                TimeSlot snappedWindow = new TimeSlot(snappedStart, window.endTime());
                candidateSlots.addAll(AvailabilityCalculator.splitIntoSlots(snappedWindow, SLOT_DURATION));
            }
        }

        // 2. Lấy các booking CONFIRMED trong ngày
        Instant dayStart = date.atStartOfDay(businessZone).toInstant();
        Instant dayEnd = date.plusDays(1).atStartOfDay(businessZone).toInstant();
        List<TimeSlot> confirmedBookings = bookingRepository.findConfirmedSlotsOverlapping(resourceId, dayStart, dayEnd);

        // 3. Trừ đi các slot đã bị đặt
        List<TimeSlot> freeSlots = AvailabilityCalculator.excludeBooked(candidateSlots, confirmedBookings);

        // Same instant boundaries as creation, including the last day of the horizon.
        Instant latestStart = now.plus(Duration.ofDays(30));
        freeSlots = freeSlots.stream()
                .filter(slot -> slot.startTime().isAfter(now) && !slot.startTime().isAfter(latestStart))
                .toList();

        List<TimeSlotResponse> availableSlotResponses = freeSlots.stream()
                .map(s -> new TimeSlotResponse(s.startTime(), s.endTime()))
                .toList();

        return new ResourceAvailabilityResponse(
                resourceId,
                date,
                businessZone.getId(),
                resource.getStatus(),
                operatingWindowResponses,
                availableSlotResponses
        );
    }

    /**
     * ALC-35: Tạo booking với kiểm tra xung đột thời gian và phân quyền nghiệp vụ.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BookingResponse createBooking(BookingCreateRequest request, UserPrincipal currentUser) {
        if (request == null) {
            throw ApiException.invalidField("request", "must not be null");
        }
        if (currentUser == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.ACCESS_DENIED, "Authentication required");
        }
        if (request.resourceId() == null) {
            throw ApiException.invalidField("resourceId", "must not be null");
        }
        if (request.startTime() == null) {
            throw ApiException.invalidField("startTime", "must not be null");
        }
        if (request.endTime() == null) {
            throw ApiException.invalidField("endTime", "must not be null");
        }
        if (!request.startTime().isBefore(request.endTime())) {
            throw ApiException.invalidField("endTime", "Booking start time must be before end time");
        }

        Instant now = clock.instant();

        // 1. Kiểm tra quy tắc thời gian cơ bản (lưới 30m, duration 30m-8h, cùng ngày, tương lai, <= 30 ngày)
        TimeSlot requestedSlot = new TimeSlot(request.startTime(), request.endTime());

        if (!BookingTimeRules.isOnGrid(requestedSlot, businessZone)) {
            throw ApiException.invalidField("startTime", "Booking start and end time must align to 30-minute intervals (:00 or :30)");
        }

        if (!BookingTimeRules.hasValidDuration(requestedSlot)) {
            throw ApiException.invalidField("duration", "Booking duration must be between 30 minutes and 8 hours");
        }

        LocalDate startDate = LocalDate.ofInstant(request.startTime(), businessZone);
        LocalDate endDate = LocalDate.ofInstant(request.endTime(), businessZone);
        if (!startDate.equals(endDate)) {
            throw ApiException.invalidField("endTime", "Booking must start and end on the same day");
        }

        if (!request.startTime().isAfter(now)) {
            throw ApiException.invalidField("startTime", "Booking start time must be in the future");
        }

        if (request.startTime().isAfter(now.plus(Duration.ofDays(30)))) {
            throw ApiException.invalidField("startTime", "Booking cannot be made more than 30 days in advance");
        }

        // 2. Xác thực actor
        User creator = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> ApiException.notFound(ErrorCode.USER_NOT_FOUND, "User not found: " + currentUser.getId()));

        if (!creator.isActive()) {
            throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED, "User account is inactive");
        }

        // 3. Xác định target user (người sở hữu booking)
        User targetUser;
        if (request.userId() == null || request.userId().equals(currentUser.getId())) {
            targetUser = creator;
        } else {
            // LỚP 1: Chặn đứng Employee (Ngăn chặn IDOR hoàn toàn ở cấp nhân viên)
            if (creator.getRole() == Role.EMPLOYEE) {
                throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED, "Employees cannot create bookings for other users");
            }

            targetUser = userRepository.findById(request.userId())
                    .orElseThrow(() -> ApiException.notFound(ErrorCode.USER_NOT_FOUND, "Target user not found: " + request.userId()));

            if (!targetUser.isActive()) {
                throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED, "Target user account is inactive");
            }

             // LỚP 2: Giới hạn Manager theo Department (Ngăn chặn IDOR chéo phòng ban)
            if (creator.getRole() == Role.MANAGER) {
                UUID mgrDeptId = creator.getDepartment() == null ? null : creator.getDepartment().getId();
                UUID targetDeptId = targetUser.getDepartment() != null ? targetUser.getDepartment().getId() : null;
                 // Manager phòng Kế toán không thể dùng IDOR để đặt hộ cho nhân viên phòng Kỹ thuật
                if (mgrDeptId == null || !mgrDeptId.equals(targetDeptId)) {
                    throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED,
                            "Manager can only create bookings for users in their own department");
                }
            }
            // LỚP 3: Admin được toàn quyền quản trị
        }

        // ALC-36: Khóa bi quan dòng Resource bằng SELECT ... FOR UPDATE (chung cơ chế khóa với đổi lịch, đổi status, xóa resource; giữ khóa đến khi commit transaction).
        Resource resource = resourceRepository.findByIdForUpdate(request.resourceId())
                .orElseThrow(() -> ApiException.notFound(ErrorCode.RESOURCE_NOT_FOUND, "Resource not found: " + request.resourceId()));

        // Trong lúc chờ giải phóng khóa, thời gian thực tế có thể đã trôi qua mốc bắt đầu của booking; cần kiểm tra lại với thời gian hiện tại.
        now = clock.instant();
        if (!request.startTime().isAfter(now)) {
            throw ApiException.invalidField("startTime", "Booking start time must be in the future");
        }
        if (request.startTime().isAfter(now.plus(Duration.ofDays(30)))) {
            throw ApiException.invalidField("startTime", "Booking cannot be made more than 30 days in advance");
        }

        if (resource.getStatus() == ResourceStatus.DELETED) {
            throw ApiException.notFound(ErrorCode.RESOURCE_NOT_FOUND, "Resource not found: " + request.resourceId());
        }

        if (resource.getStatus() != ResourceStatus.AVAILABLE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.RESOURCE_NOT_AVAILABLE,
                    "Resource is not available for booking. Current status: " + resource.getStatus());
        }

        // 5. Kiểm tra giờ hoạt động (Operating Hours)
        short dayOfWeek = (short) startDate.getDayOfWeek().getValue();
        List<ResourceSchedule> schedules = scheduleRepository
                .findByResourceIdAndDayOfWeekOrderByStartTimeAsc(resource.getId(), dayOfWeek);

        List<ScheduleWindow> operatingWindows = schedules.stream()
                .map(s -> new ScheduleWindow(startDate.getDayOfWeek(), s.getStartTime(), s.getEndTime()))
                .toList();

        boolean isInsideOperatingHours = new OperatingSchedule(operatingWindows, businessZone)
                .allows(request.startTime(), request.endTime());

        if (!isInsideOperatingHours) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.OUTSIDE_OPERATING_HOURS,
                    "Requested booking interval is outside resource operating hours");
        }

        // 6. Kiểm tra xung đột (Conflict Checking)
        if (bookingRepository.hasConfirmedConflict(resource.getId(), request.endTime(), request.startTime())) {
            throw ApiException.conflict(ErrorCode.BOOKING_TIME_CONFLICT,
                    "The requested time slot conflicts with an existing confirmed booking");
        }

        // 7. Lưu Database trong Transaction
        UUID bookingId = UuidCreator.getTimeOrderedEpoch();
        Booking booking = new Booking(
                bookingId,
                resource,
                targetUser,
                creator,
                request.startTime(),
                request.endTime(),
                request.purpose(),
                now
        );
        Booking savedBooking = bookingRepository.save(booking);

        BookingStatusHistory history = new BookingStatusHistory(
                UuidCreator.getTimeOrderedEpoch(),
                savedBooking,
                null,
                BookingStatus.CONFIRMED,
                creator,
                "Initial booking creation",
                now
        );
        bookingStatusHistoryRepository.save(history);
        notificationService.confirmBooking(savedBooking, now);

        return BookingResponse.from(savedBooking);
    }

    /** ALC-38/39/40: Chính sách hủy booking thống nhất cho Owner, Manager và Admin trước giờ bắt đầu. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void cancelBooking(UUID bookingId, BookingCancelRequest request, UserPrincipal currentUser) {
        if (currentUser == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.ACCESS_DENIED, "Authentication required");
        }
        if (bookingId == null) {
            throw ApiException.invalidField("bookingId", "must not be null");
        }
        String reason = request == null ? null : request.reason();
        if (reason != null && reason.length() > 500) {
            throw ApiException.invalidField("reason", "size must be between 0 and 500");
        }

        UUID resourceId = bookingRepository.findResourceIdById(bookingId)
                .orElseThrow(() -> bookingNotFound(bookingId));
        // Luôn chiếm khóa Resource trước Booking: giữ đúng thứ tự khóa với mọi thao tác tài nguyên để loại bỏ nguy cơ Deadlock.
        // Không yêu cầu tài nguyên phải AVAILABLE: các booking tương lai của tài nguyên bị SUSPENDED vẫn được phép hủy hợp lệ.
        resourceRepository.findByIdForUpdate(resourceId)
                .orElseThrow(() -> bookingNotFound(bookingId));
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> bookingNotFound(bookingId));
        User actor = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> ApiException.notFound(ErrorCode.USER_NOT_FOUND, "User not found"));
        if (!actor.isActive()) {
            throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED, "User account is inactive");
        }
        User owner = booking.getUser();
        boolean sameDepartmentManager = actor.getRole() == Role.MANAGER
                && actor.getDepartment() != null && owner.getDepartment() != null
                && actor.getDepartment().getId().equals(owner.getDepartment().getId());
        if (!actor.getId().equals(owner.getId()) && actor.getRole() != Role.ADMIN && !sameDepartmentManager) {
            throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED,
                    "You cannot cancel this booking");
        }

        Instant now = clock.instant();
        if (!booking.cancel(actor, reason, now)) {
            throw ApiException.conflict(ErrorCode.BOOKING_CANNOT_BE_CANCELLED,
                    "Only CONFIRMED bookings strictly before their start time can be cancelled");
        }
        bookingRepository.save(booking);
        bookingStatusHistoryRepository.save(new BookingStatusHistory(
                UuidCreator.getTimeOrderedEpoch(), booking, BookingStatus.CONFIRMED,
                BookingStatus.CANCELLED, actor, reason, now));
        notificationService.cancelBooking(booking, now);
    }

    private static ApiException bookingNotFound(UUID id) {
        return ApiException.notFound(ErrorCode.BOOKING_NOT_FOUND, "Booking not found: " + id);
    }
}
