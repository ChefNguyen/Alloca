# Domain Spec: Notifications & Background Jobs

Tài liệu đặc tả chi tiết miền nghiệp vụ **Thông báo (Notifications) và Công việc nền (Background Jobs)** cho Alloca Phase 1.  
Tài liệu này là **Single Source of Truth** cho việc thiết kế Notification Service, In-app Notification Storage, Outbox Pattern, Email API Adapter, và Spring Scheduled Tasks.

---

## 1. Quy tắc nghiệp vụ (Business Rules)

### BR-NOTIF-01 — Đã chốt (Locked Rules)
- **Ma trận sự kiện & Người nhận (Event & Recipient Matrix)**:

| Sự kiện (Event) | Người nhận (Recipients) | Kênh bắt buộc | Kênh phụ (Tùy chọn) |
| :--- | :--- | :---: | :---: |
| **Xác nhận đặt chỗ** (Booking Created) | Chủ booking (`user_id`) | In-app | Email qua HTTPS API |
| **Nhắc lịch sắp tới** (Booking Reminder) | Chủ booking (`user_id`) | In-app | Email qua HTTPS API |
| **Hủy đặt chỗ** (Booking Cancelled) | Chủ booking (`user_id`) và Người thao tác hủy (`created_by`) nếu khác nhau (khử trùng recipient) | In-app | Email qua HTTPS API |
| **Cảnh báo sử dụng Resource phòng ban** | Toàn bộ **Manager active** của phòng ban sở hữu resource | In-app | Email qua HTTPS API |
| **Cảnh báo sử dụng Resource dùng chung** | Toàn bộ **Admin active** trên hệ thống | In-app | Email qua HTTPS API |

- **Độ tin cậy của kênh thông báo**:
  - Thông báo nội bộ trong ứng dụng (**In-app Notification**) là **BẮT BUỘC** và được lưu trữ bền vững (durable) trong cơ sở dữ liệu.
  - Gửi Email qua HTTPS API bên ngoài là kênh phụ ưu tiên P2; **sự cố gửi email thất bại không bao giờ làm rollback giao dịch tạo/hủy booking**.
- **Thời gian nhắc lịch (Reminder Lead Time)**:
  - Cấu hình toàn hệ thống do Admin thiết lập (mặc định **15 phút**, cho phép chỉnh từ 1 – 1440 phút).
  - Đặt chỗ phút chót: Nếu người dùng đặt chỗ sau mốc thời gian nhắc (`createdAt > due`), hệ thống **không gửi thông báo nhắc lịch bù/hồi tố**.
- **Tính lũy thừa & Cơ chế Catch-up (Idempotent & Catch-up Jobs)**:
  - Các job chạy nền (hoàn tất booking, nhắc lịch, đánh giá alert) phải an toàn khi chạy lại nhiều lần (idempotent).
  - Môi trường cloud miễn phí (Render Free Tier) có thể "ngủ" (spin-down). Khi service thức dậy (wake-up) hoặc restart, scheduler phải tự động **quét bù (catch-up)** các tác vụ hợp lệ.

### BR-JOB-01 — Mặc định triển khai (Implementation Defaults)
- **Công thức tính mốc nhắc lịch**:
  - `due = start_time - configuredLeadTime`.
  - Một booking chỉ đủ điều kiện nhắc khi thỏa mãn tất cả:
    1. Đặt trước mốc nhắc: `created_at <= due`.
    2. Nằm trong cửa sổ quét hợp lệ: `due <= now < start_time`.
    3. Độ trễ thức dậy cho phép không quá 5 phút: `now - due <= 5 minutes` (nếu service ngủ dậy quá 5 phút kể từ `due` thì bỏ qua, không gửi để tránh làm phiền).
    4. Trạng thái hiện tại vẫn là `CONFIRMED`.
    5. Chưa từng gửi nhắc trước đó: `reminder_sent_at IS NULL`.
- **Đánh dấu nhắc lịch nguyên tử (Atomic Mark)**:
  - Cập nhật trường `reminder_sent_at = now()` trong cùng transaction tạo bản ghi thông báo in-app để chống gửi lặp khi retry.
- **Job tự động hoàn tất Booking (Completion Job)**:
  - Chạy quét định kỳ (mỗi phút) hoặc khi startup: Cập nhật mọi booking `CONFIRMED` có `end_time <= now` sang trạng thái `COMPLETED`.
  - Ghi một bản ghi vào bảng `booking_status_history` với `changed_by = null` (đại diện cho SYSTEM). Tuyệt đối không chuyển sang `CANCELLED`.
- **Job đánh giá cảnh báo sử dụng (Utilization Alert Job)**:
  - Chạy mỗi ngày lúc 00:05 (giờ `Asia/Ho_Chi_Minh`) hoặc khi restart: Đánh giá tỷ lệ sử dụng của 7 ngày đã hoàn tất gần nhất. Khi restart chỉ đánh giá kỳ hiện tại, không replay lại các ngày cũ đã qua.
- **Quyền riêng tư của Notification**:
  - Người dùng chỉ được xem và đánh dấu đã đọc (`mark-as-read`) thông báo của chính mình (`user_id = current_user`). Việc đọc thông báo không cấp thêm bất kỳ quyền truy cập dữ liệu booking ngoài phạm vi của vai trò.

---

## 2. Sơ đồ tuần tự Thông báo & Xử lý Nền (Notification Sequence)

```mermaid
%%{init: {"theme":"base","themeVariables":{"background":"#F8FAFC","actorBkg":"#ECFDF5","actorBorder":"#059669","actorTextColor":"#0F172A","actorLineColor":"#94A3B8","signalColor":"#64748B","signalTextColor":"#0F172A","labelBoxBkgColor":"#FAFAFA","labelBoxBorderColor":"#94A3B8","labelTextColor":"#0F172A","loopTextColor":"#475569","noteBkgColor":"#FAFAFA","noteBorderColor":"#CBD5E1","noteTextColor":"#475569","sequenceNumberColor":"#64748B"}}}%%
sequenceDiagram
    autonumber
    actor Actor as Người thao tác (Owner / Manager / Admin)
    participant Booking as BookingService
    participant Scheduler as Spring Scheduler (Catch-up / Periodic)
    participant Analytics as AnalyticsService
    participant Notif as NotificationService
    participant DB as PostgreSQL
    participant EmailAPI as External Email API (HTTPS)
    participant Web as React App

    rect rgb(248, 250, 252)
        Note over Actor,Web: 1. Tạo Booking thành công
        Actor->>Booking: Tạo booking CONFIRMED
        Booking->>DB: Transaction: Lưu booking + Status history + Notification Outbox
        Booking->>DB: COMMIT TRANSACTION
        Booking->>Notif: Dispatch durable notification sau commit
        opt Email được kích hoạt
            Notif->>EmailAPI: Gửi email xác nhận (Lỗi không rollback booking)
        end
    end

    rect rgb(250, 250, 250)
        Note over Scheduler,DB: 2. Job định kỳ & Quét bù (Mỗi phút)
        Scheduler->>DB: Tìm booking: due <= now < start, (now - due) <= 5m, created_at <= due, reminder_sent_at IS NULL
        loop Với từng booking đủ điều kiện
            Scheduler->>Notif: sendReminder(owner)
            Notif->>DB: INSERT notification & UPDATE reminder_sent_at = now()
        end
        Scheduler->>Booking: completeExpiredBookings()
        Booking->>DB: UPDATE bookings SET status='COMPLETED' WHERE status='CONFIRMED' AND end_time <= now()
        Booking->>DB: INSERT booking_status_history (changed_by = NULL)
    end

    rect rgb(248, 250, 252)
        Note over Actor,Web: 3. Hủy Booking hợp lệ
        Actor->>Booking: Hủy CONFIRMED (now < start)
        Booking->>DB: Transaction: UPDATE CANCELLED + Lưu Notification cho Owner & Creator
        Booking->>DB: COMMIT TRANSACTION
        Booking->>Notif: Dispatch notification hủy sau commit
    end

    rect rgb(250, 250, 250)
        Note over Scheduler,DB: 4. Job đánh giá Alert (Hàng ngày hoặc sau Wake-up)
        Scheduler->>Analytics: evaluateCurrentUtilization()
        Analytics->>DB: Tạo / Cập nhật Alert
        Analytics->>Notif: Gửi thông báo vượt ngưỡng tới Manager / Admin
    end

    Note over Web,DB: Người dùng xem danh sách thông báo cá nhân
    Web->>Notif: GET /api/v1/notifications
    Notif->>DB: SELECT * FROM notifications WHERE user_id = :currentUserId ORDER BY created_at DESC
    DB-->>Notif: Danh sách thông báo
    Notif-->>Web: 200 OK
```

---

## 3. Cấu trúc bảng Thông báo (Schema Overview)

### Bảng `notifications`:
- `id`: UUID (PK, UUIDv7).
- `user_id`: UUID (FK `users.id`, người nhận thông báo).
- `title`: String (Tiêu đề thông báo ngắn gọn).
- `message`: Text (Nội dung chi tiết).
- `type`: Enum (`BOOKING_CONFIRMED`, `BOOKING_REMINDER`, `BOOKING_CANCELLED`, `UTILIZATION_ALERT`).
- `reference_id`: UUID (ID của Booking hoặc Alert liên quan).
- `is_read`: Boolean (mặc định `false`).
- `created_at`: Timestamp UTC.

---

## 4. API Endpoints liên quan

- `GET /api/v1/notifications` — Lấy danh sách thông báo của người dùng hiện tại (hỗ trợ phân trang, lọc chưa đọc).
- `PATCH /api/v1/notifications/{id}/read` — Đánh dấu thông báo đã đọc.
- `PATCH /api/v1/notifications/read-all` — Đánh dấu toàn bộ thông báo của người dùng là đã đọc.
- `GET /api/v1/system/settings` — Xem cấu hình hệ thống bao gồm `reminder_lead_time_minutes` (Admin only).
- `PATCH /api/v1/system/settings` — Cập nhật `reminder_lead_time_minutes` (Admin only).

---

## 5. Tiêu chí kiểm thử nghiệm thu (Acceptance Test Checklist)

- [ ] **TC-NOTIF-01**: Tạo booking thành công tạo đúng 1 bản ghi `BOOKING_CONFIRMED` cho `owner` trong bảng `notifications`.
- [ ] **TC-NOTIF-02**: Quản lý đặt hộ cho nhân viên, thông báo xác nhận gửi cho chủ booking (`user_id`).
- [ ] **TC-NOTIF-03**: Khi hủy booking, nếu `created_by != user_id`, cả hai người đều nhận được thông báo hủy, không trùng lặp nếu là cùng một người.
- [ ] **TC-NOTIF-04**: Booking tạo sau mốc `due` (ví dụ tạo trước giờ họp 10 phút khi lead time là 15 phút) **không** nhận được thông báo nhắc lịch khi scheduler chạy.
- [ ] **TC-NOTIF-05**: Khi scheduler chạy quét, booking đủ điều kiện nhận đúng 1 thông báo nhắc lịch và `reminder_sent_at` được gán mốc thời gian; lần chạy kế tiếp không gửi lại.
- [ ] **TC-NOTIF-06**: Khi `end_time <= now`, job tự động chuyển trạng thái booking sang `COMPLETED` và lịch sử trạng thái có `changed_by = null`.
- [ ] **TC-NOTIF-07**: Người dùng A không thể đọc hoặc đánh dấu đã đọc thông báo của Người dùng B (`404` hoặc `403`).
