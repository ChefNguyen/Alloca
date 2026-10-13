# Domain Spec: Booking & Time Engine

Tài liệu đặc tả chi tiết miền nghiệp vụ **Booking và Thời gian (Booking & Time Engine)** cho Alloca Phase 1.  
Tài liệu này là **Single Source of Truth** cho việc thiết kế Domain Model, Service Layer, Database Lock, và Unit/Integration Test của module Booking.

---

## 1. Quy tắc nghiệp vụ (Business Rules)

### BR-TIME-01 — Đã chốt (Locked Rules)
- **Timezone nghiệp vụ**: `Asia/Ho_Chi_Minh` (UTC+7). Mọi timestamp gửi lên API phải có offset rõ ràng và response trả về chuẩn hóa UTC (ISO-8601 `Z`).
- **Lưới thời gian (Slot Grid)**: Bắt đầu (`start_time`) và kết thúc (`end_time`) phải chia hết cho bước **30 phút** (phút chỉ có thể là `:00` hoặc `:30`, giây và micro giây phải bằng `0`).
- **Thời lượng booking**: Tối thiểu **30 phút**, tối đa **8 giờ** (480 phút).
- **Phạm vi ngày**: Booking **không được kéo dài qua ngày địa phương** (`start_time` và `end_time` phải cùng một ngày theo giờ `Asia/Ho_Chi_Minh`).
- **Khoảng thời gian đặt trước (Horizon)**: Cho phép đặt tối đa **30 ngày** tính từ thời điểm hiện tại của server (`serverNow <= start <= serverNow + 30 days`).
- **Không đặt trong quá khứ**: `start_time > serverNow` (kiểm tra trên đồng hồ server, không tin đồng hồ client).
- **Điều kiện hợp lệ**:
  - Toàn bộ khoảng booking `[start, end)` phải nằm trọn trong các khung giờ hoạt động (`operating_windows`) của resource.
  - Resource phải ở trạng thái `AVAILABLE`.
  - Không trùng với bất kỳ booking nào đang ở trạng thái `CONFIRMED`.
- **Khoảng nửa mở `[start, end)`**:
  - Hai booking bị coi là trùng/giao nhau khi: `start < existing.end AND end > existing.start`.
  - Hai booking tiếp giáp nhau (ví dụ: Booking 1 kết thúc lúc 10:00, Booking 2 bắt đầu lúc 10:00) được **phép** và không bị coi là xung đột.

### BR-TIME-02 — Mặc định triển khai (Implementation Defaults)
- **Kiểm tra tức thời (Strict Containment)**:
  - Khung giờ hoạt động theo tuần (`operating_windows`): các window trong cùng một ngày không overlap, `start < end`. Các window tiếp giáp nhau được hợp nhất để kiểm tra booking có nằm trọn trong đó hay không.
  - Ngày không có window tức là đóng cửa. Phase 1 không có lịch nghỉ lễ riêng (holiday calendar).
- **Lưới 30 phút độc lập**: Khung giờ hoạt động có thể bắt đầu ở phút lẻ (ví dụ 08:15 - 17:45), nhưng booking vẫn bắt buộc phải snap theo lưới 30 phút (ví dụ 08:30 - 17:30) nằm trọn trong đó.
- **Tính toán khả dụng (Availability)**:
  - API kiểm tra slot trống chỉ trả về các khoảng còn đặt được tính từ hiện tại (`> now`), loại bỏ các slot quá khứ.
  - Không có cơ chế giữ chỗ tạm (soft-lock/cart). Việc xem slot trống không cam kết giữ chỗ; khi gọi `POST /api/v1/bookings` hệ thống luôn khóa dòng và kiểm tra lại trên dữ liệu thật.
- **Bất biến (Immutability)**:
  - Phase 1 **không hỗ trợ sửa thời gian hoặc đổi resource** của booking đã tạo (`UPDATE /bookings/{id}` bị cấm).
  - Người dùng phải **HỦY (Cancel)** booking cũ và **TẠO MỚI (Create)** booking mới theo đúng quy trình.
- **Hủy booking**:
  - Chỉ được hủy khi trạng thái còn `CONFIRMED` và `now < start_time` (chưa đến giờ bắt đầu).
  - Khi booking đã đến giờ bắt đầu hoặc đang diễn ra (`now >= start_time`), không ai (kể cả Admin) được hủy qua flow thông thường trong Phase 1.

---

## 2. Sơ đồ trạng thái Booking (State Machine)

Booking tuân theo vòng đời trạng thái nghiêm ngặt, xử lý chuyển trạng thái đơn hướng:

```mermaid
%%{init: {"theme":"base","themeVariables":{"background":"#F8FAFC","primaryColor":"#EEF2FF","primaryBorderColor":"#4F46E5","primaryTextColor":"#0F172A","lineColor":"#64748B","textColor":"#0F172A","noteBkgColor":"#FAFAFA","noteBorderColor":"#CBD5E1","noteTextColor":"#475569"}}}%%
stateDiagram-v2
    state "Vòng đời booking" as Booking {
        [*] --> CONFIRMED: Tạo booking hợp lệ (có khóa DB)
        CONFIRMED --> CANCELLED: Owner hủy trước giờ bắt đầu
        CONFIRMED --> CANCELLED: Manager hủy owner cùng department (now < start)
        CONFIRMED --> CANCELLED: Admin hủy toàn hệ thống (now < start)
        CONFIRMED --> COMPLETED: Scheduler/catch-up khi end_time <= now
        CANCELLED --> [*]
        COMPLETED --> [*]
    }

    note right of CONFIRMED
        Booking mới:
        - Cùng ngày Asia/Ho_Chi_Minh
        - Bước 30 phút (30m - 8h)
        - Đặt trước tối đa 30 ngày
        - start > serverNow
        - Resource AVAILABLE
        - Nằm trọn trong operating schedule
    end note

    note right of COMPLETED
        Tự động chuyển bởi hệ thống (changed_by = null).
        Hoàn tất mang tính idempotent.
    end note
```

---

## 3. Kiểm soát tương tranh & Cơ chế khóa (Concurrency Control)

Để tránh tình trạng đặt trùng phòng (double-booking) khi có nhiều request đồng thời, hệ thống sử dụng **Pessimistic Locking (`SELECT ... FOR UPDATE`)** trên dòng `Resource`:

```mermaid
%%{init: {"theme":"base","themeVariables":{"background":"#F8FAFC","actorBkg":"#EFF6FF","actorBorder":"#2563EB","actorTextColor":"#0F172A","actorLineColor":"#94A3B8","signalColor":"#64748B","signalTextColor":"#0F172A","labelBoxBkgColor":"#FAFAFA","labelBoxBorderColor":"#94A3B8","labelTextColor":"#0F172A","loopTextColor":"#475569","noteBkgColor":"#FAFAFA","noteBorderColor":"#CBD5E1","noteTextColor":"#475569","sequenceNumberColor":"#64748B"}}}%%
sequenceDiagram
    autonumber
    actor UserA as Nhân viên A
    actor UserB as Nhân viên B
    participant API as BookingController
    participant TxA as BookingService Tx A
    participant TxB as BookingService Tx B
    participant DB as PostgreSQL
    participant Notif as NotificationService

    par Hai yêu cầu gửi gần như đồng thời
        UserA->>API: POST /api/v1/bookings (cùng resource và thời gian)
        API->>TxA: createBooking(command A)
    and
        UserB->>API: POST /api/v1/bookings (cùng resource và thời gian)
        API->>TxB: createBooking(command B)
    end

    Note over API,DB: Kiểm tra authentication active & scope RBAC
    TxA->>DB: BEGIN TRANSACTION
    TxA->>DB: SELECT * FROM resources WHERE id = :id FOR UPDATE
    DB-->>TxA: Khóa hàng resource thành công cho Tx A

    TxB->>DB: BEGIN TRANSACTION
    TxB->>DB: SELECT * FROM resources WHERE id = :id FOR UPDATE
    Note over TxB,DB: Tx B bị block, chờ Tx A giải phóng khóa

    TxA->>DB: Kiểm tra: status=AVAILABLE, schedule valid, no overlapping CONFIRMED
    DB-->>TxA: Hợp lệ (không có booking trùng)
    TxA->>DB: INSERT INTO bookings (...) VALUES ('CONFIRMED')
    TxA->>DB: INSERT INTO booking_status_history (...)
    TxA->>DB: INSERT INTO notification_outbox (...)
    TxA->>DB: COMMIT TRANSACTION
    Note over TxA,DB: Commit giải phóng khóa trên resource
    DB-->>TxB: Tx B nhận được khóa resource

    TxA->>Notif: Dispatch durable notification sau commit
    TxA-->>API: 201 Created (Booking A)
    API-->>UserA: Trả về thông tin Booking

    TxB->>DB: Kiểm tra booking CONFIRMED bị trùng
    DB-->>TxB: Phát hiện Booking A đã tồn tại và overlap!
    TxB->>DB: ROLLBACK
    TxB-->>API: Ném BookingConflictException
    API-->>UserB: 409 Conflict ("Resource is already booked for this interval")
```

---

## 4. Luồng xử lý chi tiết (Activity Flow)

```mermaid
%%{init: {"theme":"base","themeVariables":{"background":"#F8FAFC","primaryColor":"#EEF2FF","primaryBorderColor":"#4F46E5","primaryTextColor":"#0F172A","lineColor":"#64748B","textColor":"#0F172A","clusterBkg":"#F8FAFC","clusterBorder":"#E2E8F0","edgeLabelBackground":"#F8FAFC"},"flowchart":{"curve":"basis","htmlLabels":true}}}%%
flowchart TD
    start(["Bắt đầu: Đặt chỗ"])
    request["POST /api/v1/bookings"]
    authenticated{"Token hợp lệ & User active?"}
    inputValid{"Thời gian hợp lệ:<br/>- Không quá khứ<br/>- Cùng ngày<br/>- Lưới 30p (30p-8h)<br/>- Trong 30 ngày"}
    authorized{"Đúng quyền tạo booking?<br/>- Self: OK<br/>- Manager: user cùng department<br/>- Admin: toàn quyền"}
    beginTx["Bắt đầu Transaction"]
    lock["Khóa resource:<br/>SELECT FOR UPDATE"]
    exists{"Resource tồn tại & status != DELETED?"}
    available{"Resource status == AVAILABLE?"}
    inSchedule{"Nằm trọn trong operating windows?"}
    conflict{"Trùng booking CONFIRMED?<br/>start < existing.end AND end > existing.start"}
    insert["Lưu Booking CONFIRMED,<br/>Status History,<br/>Notification Outbox"]
    commit["Commit & nhả Lock"]
    success(["201 Created"])
    rollback["Rollback"]
    unauthorized(["401 Unauthorized"])
    forbidden(["403 Forbidden"])
    badRequest(["400 Bad Request"])
    notFound(["404 Not Found"])
    unavailable(["409 Resource Unavailable"])
    overlap(["409 Conflict"])

    start --> request --> authenticated
    authenticated -- "Không" --> unauthorized
    authenticated -- "Có" --> inputValid
    inputValid -- "Không" --> badRequest
    inputValid -- "Có" --> authorized
    authorized -- "Không" --> forbidden
    authorized -- "Có" --> beginTx --> lock --> exists
    exists -- "Không" --> rollback --> notFound
    exists -- "Có" --> available
    available -- "Không" --> rollback --> unavailable
    available -- "Có" --> inSchedule
    inSchedule -- "Không" --> rollback --> badRequest
    inSchedule -- "Có" --> conflict
    conflict -- "Có" --> rollback --> overlap
    conflict -- "Không" --> insert --> commit --> success
```

---

## 5. API Endpoints liên quan

- `POST /api/v1/bookings` — Tạo booking mới.
- `GET /api/v1/bookings` — Lấy danh sách booking theo phân quyền (Employee: của mình; Manager: của department; Admin: all).
- `GET /api/v1/bookings/{id}` — Lấy chi tiết booking.
- `DELETE /api/v1/bookings/{id}` — Hủy booking (`CONFIRMED` và `now < start_time`).
- `GET /api/v1/resources/{resourceId}/availability` — Kiểm tra các khoảng thời gian trống theo ngày.

---

## 6. Tiêu chí kiểm thử nghiệm thu (Acceptance Test Checklist)

- [ ] **TC-BOOK-01**: Tạo booking với phút lẻ (ví dụ 09:15) trả về `400 Bad Request`.
- [ ] **TC-BOOK-02**: Tạo booking dưới 30 phút hoặc trên 8 giờ trả về `400 Bad Request`.
- [ ] **TC-BOOK-03**: Tạo booking qua đêm (23:00 hôm nay đến 01:00 hôm sau) trả về `400 Bad Request`.
- [ ] **TC-BOOK-04**: Tạo booking với `start_time` trong quá khứ trả về `400 Bad Request`.
- [ ] **TC-BOOK-05**: Tạo booking vượt quá 30 ngày tới trả về `400 Bad Request`.
- [ ] **TC-BOOK-06**: Hai booking tiếp giáp nhau (09:00 - 10:00 và 10:00 - 11:00) đều thành công `201 Created`.
- [ ] **TC-BOOK-07**: Hai booking giao nhau 1 phút (09:00 - 10:00 và 09:30 - 10:30) booking sau bị `409 Conflict`.
- [ ] **TC-BOOK-08 (Concurrency)**: Chạy 10 luồng song song đặt cùng 1 slot trên 1 resource, chỉ duy nhất 1 luồng trả về `201`, 9 luồng còn lại trả về `409 Conflict`.
- [ ] **TC-BOOK-09**: Hủy booking sau khi đã bắt đầu (`now >= start`) trả về `400/409 Error`.
- [ ] **TC-BOOK-10**: Scheduler tự động đổi booking sang `COMPLETED` khi `end_time <= now`.
