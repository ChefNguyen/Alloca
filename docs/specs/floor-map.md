# Domain Spec: Interactive Floor Map & Visual Booking

Tài liệu đặc tả chi tiết miền nghiệp vụ **Sơ đồ mặt bằng tương tác (Floor Map) và Đặt chỗ trực quan** cho Alloca Phase 1.  
Tài liệu này là **Single Source of Truth** cho việc thiết kế Floor Map Service, Image Upload Validation, Coordinate Mapping và Visual Availability Checking.

---

## 1. Quy tắc nghiệp vụ (Business Rules)

### BR-MAP-01 — Đã chốt (Locked Rules)
- **Quy trình đặt chỗ 2 bước trên Sơ đồ (Two-Step Visual Flow)**:
  1. **Bước 1 (Xem ngày)**: Người dùng chọn ngày cần xem (`localDate`). Bản đồ hiển thị tổng quan trạng thái cả ngày của từng tài nguyên qua màu sắc marker.
  2. **Bước 2 (Chọn khung giờ cụ thể)**: Người dùng chọn giờ bắt đầu và kết thúc (`start_time`, `end_time`) theo bước 30 phút. Bản đồ tự động đánh giá lại trạng thái thời gian thực và đổi màu:
     - **Xanh (Available)**: Toàn bộ khoảng thời gian chọn có thể đặt được. Người dùng click marker để mở modal xác nhận đặt chỗ.
     - **Đỏ (Unavailable)**: Khoảng thời gian đã bị trùng booking hoặc ngoài giờ hoạt động. Marker bị vô hiệu hóa đặt chỗ.
- **Định dạng file sơ đồ**:
  - Tải lên ảnh raster định dạng **PNG hoặc JPEG** (tối đa **10 MiB**).
  - Định dạng **SVG chỉ được dùng làm lớp phủ đồ họa (SVG Overlay) do frontend sinh ra** để vẽ các marker/khối tương tác; **hệ thống tuyệt đối không nhận file SVG do người dùng tải lên** (nhằm triệt tiêu rủi ro tấn công XSS / XML Injection).

### BR-MAP-02 — Mặc định triển khai (Implementation Defaults)
- **Mã màu trạng thái theo ngày (Day-level Marker Colors)**:
  - **Xanh lá (Green)**: Mọi slot hợp lệ trong ngày đều còn trống (chưa có ai đặt).
  - **Vàng (Yellow)**: Còn một phần slot trống (đã có người đặt một số khung giờ, nhưng vẫn còn ít nhất một slot hợp lệ dài $\ge$ 30 phút).
  - **Đỏ (Red)**: Toàn bộ các slot hợp lệ đã kín chỗ hoặc ngày đó không còn bất kỳ slot trống nào.
  - **Xám (Gray)**: Resource đang ở trạng thái `SUSPENDED` (không cho phép click đặt chỗ).
  - **Ẩn hoàn toàn (Hidden)**: Resource ở trạng thái `DELETED` không hiển thị trên sơ đồ đối với người dùng thông thường.
  - *Lưu ý*: Ngày đóng cửa hoặc chỉ còn các slot trong quá khứ (`<= now`) **không bao giờ được tô màu xanh**.
- **Mã màu trạng thái theo khung giờ đã chọn (Interval-level Marker Colors)**:
  - **Xanh lá (Green)**: Toàn bộ khoảng thời gian lựa chọn trống và đặt được.
  - **Đỏ (Red)**: Khoảng thời gian bị trùng hoặc không đặt được. Tuyệt đối không dùng màu vàng ở bước này để tránh gây hiểu lầm.
  - Không dùng màu sắc làm tín hiệu duy nhất: Phải kèm theo nhãn chữ (Label / Tooltip / Legend) để hỗ trợ Accessibility.
- **Hệ tọa độ & Kích thước ảnh (Pixel Space Mapping)**:
  - Tọa độ marker (`map_x`, `map_y`) và kích thước (`map_width`, `map_height`) được lưu theo **Pixel thực tế của ảnh gốc**.
  - Bảng `zones` lưu thêm `floor_map_width` và `floor_map_height` của ảnh để frontend dễ dàng tính tỷ lệ co giãn (responsive scale) trên các kích thước màn hình.
  - Tọa độ và kích thước phải là số nguyên dương và nằm trọn trong giới hạn kích thước của ảnh.
- **Lưu trữ & Bảo mật Upload**:
  - File ảnh được lưu trữ trên Cloudflare R2 (S3-compatible API). Backend chỉ lưu URL công khai và kích thước ảnh vào bảng `zones`.
  - Kiểm tra file upload thông qua cả Magic Bytes / Content-Type thực tế, không chỉ tin đuôi file mở rộng.

---

## 2. Sơ đồ tuần tự tương tác Sơ đồ mặt bằng (Floor Map Flow)

```mermaid
%%{init: {"theme":"base","themeVariables":{"background":"#F8FAFC","actorBkg":"#EFF6FF","actorBorder":"#2563EB","actorTextColor":"#0F172A","actorLineColor":"#94A3B8","signalColor":"#64748B","signalTextColor":"#0F172A","labelBoxBkgColor":"#FAFAFA","labelBoxBorderColor":"#94A3B8","labelTextColor":"#0F172A","loopTextColor":"#475569","noteBkgColor":"#ECFDF5","noteBorderColor":"#6EE7B7","noteTextColor":"#064E3B","sequenceNumberColor":"#64748B"}}}%%
sequenceDiagram
    autonumber
    actor Admin
    actor User as Nhân viên
    participant Web as React App
    participant MapAPI as FloorMapController
    participant MapService as FloorMapService
    participant BookingService as BookingService
    participant R2 as Cloudflare R2 Storage
    participant DB as PostgreSQL

    rect rgb(248, 250, 252)
        Note over Admin,DB: A. Quản trị cấu hình Sơ đồ & Marker (Admin)
        Admin->>Web: Chọn ảnh mặt bằng PNG/JPEG
        Web->>MapAPI: PUT /api/v1/zones/{zoneId}/floor-map (multipart/form-data)
        MapAPI->>MapAPI: Validate dung lượng <= 10MiB & Magic bytes PNG/JPEG
        MapAPI->>R2: Upload ảnh qua S3 API
        R2-->>MapAPI: Trả về Public Image URL
        MapAPI->>DB: UPDATE zones SET floor_map_url, width, height WHERE id = :zoneId
        MapAPI-->>Web: 200 OK (Ảnh mặt bằng đã cập nhật)

        Admin->>Web: Kéo thả gán vị trí Resource lên bản đồ
        Web->>MapAPI: PATCH /api/v1/resources/{resourceId} (map_x, map_y, map_width, map_height)
        MapAPI->>DB: Kiểm tra tọa độ nằm trong ảnh & Lưu tọa độ
        DB-->>MapAPI: Thành công
        MapAPI-->>Web: 200 OK
    end

    rect rgb(250, 250, 250)
        Note over User,DB: B. Người dùng xem & Đặt chỗ trực quan
        User->>Web: Mở Zone và chọn Ngày (localDate)
        Web->>MapAPI: GET /api/v1/zones/{zoneId}/map-availability?date=YYYY-MM-DD
        MapAPI->>MapService: getDayAvailability(zoneId, localDate)
        MapService->>DB: Lấy resources, operating schedules và booking CONFIRMED trong ngày
        DB-->>MapService: Dữ liệu đặt chỗ trong ngày
        MapService-->>MapAPI: Danh sách marker kèm màu (GREEN / YELLOW / RED / GRAY)
        MapAPI-->>Web: 200 OK + Danh sách Marker
        Web-->>User: Render Floor Map với màu theo ngày

        User->>Web: Chọn khoảng giờ cụ thể (09:00 - 10:30)
        Web->>MapAPI: GET /api/v1/zones/{zoneId}/map-availability/check?start=...&end=...
        MapAPI->>DB: Kiểm tra tính khả dụng của từng resource trong khoảng đã chọn
        MapAPI-->>Web: marker.available = true (Xanh) hoặc false (Đỏ)

        alt Resource còn trống trong khoảng đã chọn (Xanh)
            User->>Web: Click vào marker phòng họp
            Web->>BookingService: POST /api/v1/bookings
            BookingService->>DB: Khóa resource (SELECT FOR UPDATE) & Lưu booking CONFIRMED
            BookingService-->>Web: 201 Created
            Web-->>User: Đặt chỗ thành công!
        else Resource bị trùng hoặc ngoài giờ (Đỏ)
            Web-->>User: Marker bị vô hiệu hóa click; hiển thị lý do
        end
    end
```

---

## 3. Cấu trúc lưu trữ dữ liệu Sơ đồ

### Bảng `zones`:
- `id`: UUID (PK).
- `name`: String (Tên khu vực, ví dụ "Tầng 3 - Tòa nhà A").
- `floor_map_url`: String (URL ảnh trên R2, nullable).
- `floor_map_width`: Integer (Chiều rộng ảnh gốc tính bằng pixel).
- `floor_map_height`: Integer (Chiều cao ảnh gốc tính bằng pixel).

### Các trường trên bảng `resources`:
- `map_x`: Integer (Tọa độ X tính bằng pixel từ góc trên bên trái ảnh).
- `map_y`: Integer (Tọa độ Y tính bằng pixel).
- `map_width`: Integer (Độ rộng marker/bàn tính bằng pixel).
- `map_height`: Integer (Độ cao marker/bàn tính bằng pixel).

---

## 4. API Endpoints liên quan

- `PUT /api/v1/zones/{zoneId}/floor-map` — Upload ảnh sơ đồ cho zone (Admin only).
- `DELETE /api/v1/zones/{zoneId}/floor-map` — Xóa ảnh sơ đồ (Admin only).
- `GET /api/v1/zones/{zoneId}/map-availability` — Lấy trạng thái marker toàn bộ zone theo ngày (`?date=YYYY-MM-DD`).
- `GET /api/v1/zones/{zoneId}/map-availability/check` — Kiểm tra tính khả dụng của các marker theo khoảng giờ cụ thể (`?start=...&end=...`).

---

## 5. Tiêu chí kiểm thử nghiệm thu (Acceptance Test Checklist)

- [ ] **TC-MAP-01**: Upload file SVG hoặc file `.exe` giả đuôi `.png` bị từ chối với lỗi `400 Bad Request` ("Invalid image format").
- [ ] **TC-MAP-02**: Upload file ảnh dung lượng > 10 MiB bị từ chối với lỗi `413 Payload Too Large`.
- [ ] **TC-MAP-03**: Gán vị trí resource có tọa độ âm hoặc vượt quá kích thước ảnh của zone bị trả về `400 Bad Request`.
- [ ] **TC-MAP-04**: Truy vấn `map-availability` cho một ngày có slot trống và slot đã đặt trả về đúng mã màu `YELLOW` cho resource đó.
- [ ] **TC-MAP-05**: Truy vấn `map-availability` vào một ngày mà resource đang `SUSPENDED` trả về mã màu `GRAY` và cờ `bookable = false`.
- [ ] **TC-MAP-06**: Resource `DELETED` không xuất hiện trong payload trả về của `map-availability`.
- [ ] **TC-MAP-07**: Khi kiểm tra một khoảng giờ cụ thể (`map-availability/check`), nếu khoảng đó trùng với booking `CONFIRMED` thì marker trả về `available = false`.
