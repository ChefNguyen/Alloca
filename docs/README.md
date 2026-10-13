# Alloca — Trung tâm Tài liệu Dự án (Documentation Hub)

Chào mừng bạn đến với tài liệu kỹ thuật và nghiệp vụ của **Alloca — Shared Resource Booking System (Phase 1)**.  
Hệ thống tài liệu này được cấu trúc theo phương pháp **Spec-Driven Development (SDD)**, phục vụ cả lập trình viên và các AI Coding Agent (như Claude Code) tra cứu chính xác, độc lập theo từng miền nghiệp vụ.

---

## 🗺️ Bản đồ Tài liệu (Documentation Map & SSoT)

```
docs/
├── README.md                           <-- (Bạn đang ở đây) Mục lục & Hướng dẫn tra cứu tổng quan
├── SRS.md                              <-- Đặc tả yêu cầu phần mềm chuẩn học thuật (IEEE 830 baseline)
├── business-rules.md                   <-- Bảng tổng hợp toàn bộ quy tắc nghiệp vụ đã chốt
├── test-plan.md                        <-- Kế hoạch kiểm thử & Acceptance Criteria
│
├── specs/                              <-- 🎯 SINGLE SOURCE OF TRUTH THEO TỪNG DOMAIN (Kèm Mermaid)
│   ├── booking-and-time.md             <-- Đặt chỗ, lưới 30p, kiểm soát xung đột [start, end), Locking
│   ├── resources-and-schedules.md      <-- Vòng đời Resource, Lịch hoạt động, Xóa mềm an toàn
│   ├── rbac-and-security.md            <-- Phân quyền Employee/Manager/Admin, JWT, credentials_version
│   ├── notifications-and-jobs.md       <-- Ma trận thông báo, Scheduler catch-up, Tự động hoàn tất
│   ├── utilization-and-alerts.md       <-- Công thức tính tỷ lệ đặt chỗ, Job quét 7 ngày, Deduplication
│   └── floor-map.md                    <-- Sơ đồ mặt bằng tương tác, Màu sắc trạng thái, Tọa độ pixel
│
├── architecture/                       <-- Kiến trúc kỹ thuật & Mô hình triển khai
│   ├── README.md                       <-- Tổng quan N-Tier, Hexagonal Package, Deployment Free Tier
│   └── ... (.mmd diagrams)
│
├── database/                           <-- Thiết kế Cơ sở dữ liệu & Mô hình dữ liệu
│   ├── README.md                       <-- Quy ước schema, UUIDv7, Composite Indexes, Soft-delete
│   └── ... (erd.mmd)
│
├── api/                                <-- HTTP API Contract (OpenAPI 3.0)
│   ├── openapi.yaml                    <-- OpenAPI 3.0 specification hoàn chỉnh
│   └── README.md                       <-- Hướng dẫn tích hợp API & Error Response RFC 7807
│
├── backlog/                            <-- Danh mục tính năng & User Stories
│   └── README.md
│
├── archive/                            <-- Bản lưu trữ tài liệu các phiên bản cũ
└── tools/                              <-- Scripts tự động hóa (build_srs.py sinh PDF)
```

---

## 📋 Danh mục Đặc tả Nghiệp vụ (Domain Specifications)

Khi triển khai hoặc viết test cho một tính năng, hãy đọc trực tiếp tài liệu trong thư mục [docs/specs/](file:///c:/Users/thanh/OneDrive/Documents/Project/Alloca/docs/specs/):

| Miền nghiệp vụ (Domain) | File đặc tả | Quy tắc cốt lõi | Sơ đồ đi kèm |
| :--- | :--- | :--- | :--- |
| **Booking & Time** | [booking-and-time.md](file:///c:/Users/thanh/OneDrive/Documents/Project/Alloca/docs/specs/booking-and-time.md) | BR-TIME-01, BR-TIME-02 | State Machine, Concurrency Lock Sequence, Activity Flow |
| **Resources & Schedules** | [resources-and-schedules.md](file:///c:/Users/thanh/OneDrive/Documents/Project/Alloca/docs/specs/resources-and-schedules.md) | BR-RESOURCE-01, BR-RESOURCE-02 | Resource State Machine |
| **RBAC & Security** | [rbac-and-security.md](file:///c:/Users/thanh/OneDrive/Documents/Project/Alloca/docs/specs/rbac-and-security.md) | BR-SCOPE-01, BR-SCOPE-02, BR-AUTH-01, BR-AUTH-02 | RBAC Flowchart, Auth Sequence |
| **Notifications & Jobs** | [notifications-and-jobs.md](file:///c:/Users/thanh/OneDrive/Documents/Project/Alloca/docs/specs/notifications-and-jobs.md) | BR-NOTIF-01, BR-JOB-01 | Notification & Job Sequence |
| **Utilization & Alerts** | [utilization-and-alerts.md](file:///c:/Users/thanh/OneDrive/Documents/Project/Alloca/docs/specs/utilization-and-alerts.md) | BR-STAT-01, BR-STAT-02 | Alert Evaluation Flow |
| **Floor Map** | [floor-map.md](file:///c:/Users/thanh/OneDrive/Documents/Project/Alloca/docs/specs/floor-map.md) | BR-MAP-01, BR-MAP-02 | Floor Map Interaction Flow |

---

## 🤖 Hướng dẫn dành cho AI Coding Agent (Claude Code / Subagents)

Khi bạn được giao nhiệm vụ code một tính năng cụ thể:
1. **Không cần đọc toàn bộ thư mục docs**: Chỉ cần đọc file domain tương ứng trong `docs/specs/<feature>.md` và đối chiếu với `docs/api/openapi.yaml`.
2. **Tuân thủ Acceptance Test Checklist**: Mỗi file spec đều có sẵn checklist kiểm thử ở mục cuối. Hãy dùng checklist này để viết Unit/Integration test (TDD) trước khi implement code.
3. **Tuân thủ quy ước Concurrency**: Mọi logic sửa đổi booking, resource status, và schedule đều phải dùng `SELECT ... FOR UPDATE` như mô tả trong sơ đồ tuần tự.
