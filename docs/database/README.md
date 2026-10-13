# Thiết kế Cơ sở Dữ liệu (Database Design & ERD)

Tài liệu đặc tả chi tiết mô hình dữ liệu quan hệ của hệ thống **Alloca Phase 1**, vận hành trên nền **PostgreSQL 15+ (Supabase)**.

---

## 1. Sơ đồ Thực thể Quan hệ (Entity Relationship Diagram - ERD)

```mermaid
%%{init: {"theme":"base","themeVariables":{"background":"#F8FAFC","primaryColor":"#F8FAFC","primaryBorderColor":"#334155","primaryTextColor":"#0F172A","lineColor":"#94A3B8","textColor":"#0F172A","clusterBkg":"#F8FAFC","clusterBorder":"#E2E8F0","entityBkgColor":"#F8FAFC","entityBorderColor":"#334155","attributeBackgroundColorOdd":"#F8FAFC","attributeBackgroundColorEven":"#F8FAFC"}}}%%
erDiagram
    DEPARTMENTS ||--o{ USERS : "quản lý"
    DEPARTMENTS o|--o{ RESOURCES : "sở hữu"
    ZONES ||--o{ RESOURCES : "chứa"
    RESOURCES ||--o{ RESOURCE_SCHEDULES : "có giờ hoạt động"
    RESOURCES ||--o{ BOOKINGS : "được đặt"
    RESOURCES ||--o{ ALERTS : "phát sinh"
    USERS ||--o{ BOOKINGS : "chủ booking"
    USERS ||--o{ BOOKINGS : "tạo hoặc hủy"
    USERS ||--o{ NOTIFICATIONS : "nhận"
    USERS o|--o{ ACTIVITY_LOGS : "actor nullable SYSTEM"
    USERS o|--o{ BOOKING_STATUS_HISTORY : "actor nullable SYSTEM"
    BOOKINGS ||--o{ BOOKING_STATUS_HISTORY : "theo dõi"
    BOOKINGS ||--o{ NOTIFICATIONS : "nguồn sự kiện"
    ALERTS o|--o{ NOTIFICATIONS : "nguồn sự kiện"
    BOOKINGS ||--o{ NOTIFICATION_OUTBOX : "phát sinh durable"
    ALERTS ||--o{ NOTIFICATION_OUTBOX : "phát sinh durable"

    DEPARTMENTS {
        uuid id PK
        string name UK
        timestamp created_at
    }

    USERS {
        uuid id PK
        string username UK
        string email UK
        string password_hash
        string full_name
        string role "EMPLOYEE, MANAGER, ADMIN"
        boolean is_active "mặc định true"
        int credentials_version "tăng khi đổi pass"
        uuid department_id FK "nullable"
        timestamp created_at
        timestamp updated_at
    }

    ZONES {
        uuid id PK
        string name
        string floor
        string building
        string floor_map_url "nullable"
        int floor_map_width "pixel ảnh gốc"
        int floor_map_height "pixel ảnh gốc"
    }

    RESOURCES {
        uuid id PK
        string name
        string type "MEETING_ROOM, DESK, EQUIPMENT"
        int capacity
        string status "AVAILABLE, SUSPENDED, DELETED"
        string description
        uuid zone_id FK
        uuid department_id FK "nullable = shared"
        float utilization_threshold "nullable override, mặc định 85%"
        int map_x "tọa độ pixel"
        int map_y "tọa độ pixel"
        int map_width "pixel"
        int map_height "pixel"
        timestamp created_at
        timestamp updated_at
    }

    RESOURCE_SCHEDULES {
        uuid id PK
        uuid resource_id FK
        int day_of_week "1=Monday .. 7=Sunday"
        time start_time
        time end_time
    }

    BOOKINGS {
        uuid id PK
        uuid resource_id FK
        uuid user_id FK "chủ booking"
        uuid created_by FK "người đặt hộ/tạo"
        timestamptz start_time "Asia/Ho_Chi_Minh offset, lưu UTC"
        timestamptz end_time "Asia/Ho_Chi_Minh offset, lưu UTC"
        string status "CONFIRMED, CANCELLED, COMPLETED"
        string purpose
        timestamptz reminder_sent_at "nullable"
        timestamp created_at
        timestamp updated_at
    }

    BOOKING_STATUS_HISTORY {
        uuid id PK
        uuid booking_id FK
        string from_status
        string to_status
        uuid changed_by FK "nullable = SYSTEM"
        string reason
        timestamp created_at
    }

    NOTIFICATIONS {
        uuid id PK
        uuid user_id FK "người nhận"
        string title
        string message
        string type
        uuid reference_id "booking_id hoặc alert_id"
        boolean is_read "mặc định false"
        timestamp created_at
    }

    NOTIFICATION_OUTBOX {
        uuid id PK
        string channel "EMAIL"
        string recipient
        string payload
        string status "PENDING, SENT, FAILED"
        int retry_count
        timestamp created_at
        timestamp sent_at
    }

    ALERTS {
        uuid id PK
        uuid resource_id FK
        string alert_type "HIGH_UTILIZATION"
        string status "OPEN, ACKNOWLEDGED, RESOLVED"
        float rate_percent
        float threshold_percent
        date period_from
        date period_to
        uuid acknowledged_by FK "nullable"
        timestamp created_at
        timestamp resolved_at
    }

    ACTIVITY_LOGS {
        uuid id PK
        uuid actor_id FK "nullable = SYSTEM"
        string action
        string target_type
        uuid target_id
        jsonb metadata
        timestamp created_at
    }
```

---

## 2. Quy ước Thiết kế & Best Practices

1. **Khóa chính (Primary Keys)**:
   - Toàn bộ các bảng sử dụng kiểu dữ liệu `UUID` (khuyến nghị **UUIDv7** để đảm bảo tính tuần tự theo thời gian và hiệu năng B-Tree Index vượt trội so với UUIDv4 ngẫu nhiên).
2. **Thời gian & Múi giờ**:
   - Các trường mốc thời gian đặt chỗ (`start_time`, `end_time`) sử dụng kiểu `TIMESTAMPTZ` (lưu trữ UTC, có thông tin múi giờ khi deserialize).
   - Múi giờ nghiệp vụ chuẩn là `Asia/Ho_Chi_Minh` (UTC+7).
3. **Cơ chế Khóa & Chỉ mục (Indexes)**:
   - Bảng `bookings`:
     - Bắt buộc tạo Composite Index trên `(resource_id, status, start_time, end_time)` để tối ưu hóa truy vấn kiểm tra giao khoảng thời gian (`start < existing.end AND end > existing.start`).
   - Bảng `users`:
     - Unique Index trên `username` và `email`.
     - Index trên `(id, is_active, credentials_version)` để tối ưu hóa kiểm tra token mỗi request.
4. **Bảo toàn Dữ liệu & Xóa mềm**:
   - `RESOURCES`: Xóa mềm bằng cờ `status = 'DELETED'`. Tuyệt đối không chạy lệnh `DELETE FROM resources` để bảo toàn lịch sử đặt chỗ và khóa ngoại.
   - `USERS`: Vô hiệu hóa bằng `is_active = false`. Không xóa vật lý tài khoản đã từng tạo booking hoặc xuất hiện trong audit log.
