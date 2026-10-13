# Kiến trúc Hệ thống (System Architecture)

Tài liệu đặc tả kiến trúc kỹ thuật tổng quan, mô hình thành phần, và sơ đồ triển khai của dự án **Alloca**.

---

## 1. Sơ đồ Thiết kế Hệ thống (System Design)

Alloca được thiết kế theo kiến trúc phân tầng rõ ràng (N-Tier / Hexagonal Architecture):
- **Tầng Giao diện (Client)**: Ứng dụng Single Page Application (SPA) xây dựng trên React 18, TypeScript và Vite; giao tiếp qua REST API chuẩn hóa kèm JWT Bearer Token.
- **Tầng Ứng dụng (Backend)**: Spring Boot 3 chạy trên nền Java 17/21. Gồm các lớp:
  - `Security Filter`: Xác thực JWT, kiểm tra `is_active` tức thời và thẩm định RBAC.
  - `REST Controllers`: Xử lý HTTP request, mapping DTO theo OpenAPI spec.
  - `Domain & Application Services`: Chứa toàn bộ nghiệp vụ (Booking engine, Resource lifecycle, Analytics, Notification outbox).
  - `Spring Data JPA`: Quản lý truy xuất dữ liệu với cơ chế khóa bi quan (Pessimistic Locking).
  - `Spring Scheduler`: Tác vụ nền định kỳ và cơ chế tự động quét bù (Catch-up) khi backend khởi động lại.
- **Tầng Hạ tầng (Infrastructure)**: Supabase PostgreSQL (lưu trữ dữ liệu quan hệ), Cloudflare R2 (lưu ảnh sơ đồ mặt bằng), và Email API ngoài (tùy chọn).

```mermaid
%%{init: {"theme":"base","themeVariables":{"background":"#F8FAFC","primaryColor":"#EEF2FF","primaryBorderColor":"#4F46E5","primaryTextColor":"#0F172A","lineColor":"#64748B","textColor":"#0F172A","clusterBkg":"#F8FAFC","clusterBorder":"#E2E8F0","edgeLabelBackground":"#F8FAFC"},"flowchart":{"curve":"basis","htmlLabels":true}}}%%
flowchart TB
    user["Nhân viên / Manager / Admin"]
    subgraph client["Tầng giao diện"]
        web["React 18 + TypeScript + Vite"]
        apiClient["REST API client<br/>/api/v1 + JWT"]
        web --> apiClient
    end
    subgraph backend["Spring Boot 3 / Java"]
        security["Spring Security<br/>JWT, active check mỗi request, RBAC"]
        controllers["REST Controllers<br/>/api/v1"]
        subgraph services["Tầng service"]
            authService["AuthService<br/>credentials_version"]
            resourceService["ResourceService<br/>schedule/status lock"]
            bookingService["BookingService<br/>transaction + row lock"]
            notificationService["NotificationService<br/>durable in-app/outbox"]
            analyticsService["AnalyticsService"]
            mapService["FloorMapService"]
        end
        repositories["Spring Data JPA Repositories"]
        scheduler["Tác vụ định kỳ best effort<br/>catch-up idempotent khi thức"]
        openapi["SpringDoc OpenAPI"]
        security --> controllers
        controllers --> authService & resourceService & bookingService & notificationService & analyticsService & mapService
        authService --> repositories
        resourceService --> repositories
        bookingService --> repositories
        notificationService --> repositories
        analyticsService --> repositories
        mapService --> repositories
        scheduler --> bookingService & notificationService & analyticsService
        openapi -. "tài liệu hóa" .-> controllers
    end
    subgraph infrastructure["Hạ tầng"]
        postgres[("Supabase Free<br/>PostgreSQL")]
        floorMaps[("Cloudflare R2 Free Tier<br/>ảnh sơ đồ mặt bằng")]
        emailApi["Email API qua HTTPS<br/>(tùy chọn, sau commit)"]
    end
    user --> web
    apiClient -- "HTTPS + JSON / JWT" --> security
    repositories -- "JPA / transaction / row lock<br/>qua Session Pooler" --> postgres
    mapService --> floorMaps
    notificationService -. "gửi email sau commit" .-> emailApi
    classDef frontend fill:#EFF6FF,stroke:#2563EB,stroke-width:1px,color:#0F172A;
    classDef backend fill:#EEF2FF,stroke:#4F46E5,stroke-width:1px,color:#0F172A;
    classDef database fill:#ECFDF5,stroke:#059669,stroke-width:1px,color:#0F172A;
    classDef external fill:#F8FAFC,stroke:#E2E8F0,stroke-width:1px,color:#475569;
    class web,apiClient frontend;
    class security,controllers,authService,resourceService,bookingService,notificationService,analyticsService,mapService,repositories,scheduler,openapi backend;
    class postgres,floorMaps database;
    class user,emailApi external;
```

---

## 2. Sơ đồ Triển khai (Deployment Architecture)

Mô hình triển khai Alloca hướng tới mục tiêu **vận hành demo với chi phí 0 đồng** tận dụng các dịch vụ Cloud Free Tier phổ biến:

```mermaid
%%{init: {"theme":"base","themeVariables":{"background":"#F8FAFC","primaryColor":"#EEF2FF","primaryBorderColor":"#4F46E5","primaryTextColor":"#0F172A","lineColor":"#64748B","textColor":"#0F172A","clusterBkg":"#F8FAFC","clusterBorder":"#E2E8F0","edgeLabelBackground":"#F8FAFC"},"flowchart":{"curve":"basis","htmlLabels":true}}}%%
flowchart TB
    user["Thiết bị người dùng<br/>Trình duyệt web"]

    subgraph edge["Edge và giao diện"]
        dns["Cloudflare DNS Free<br/>(tùy chọn khi có tên miền)"]
        frontend["Vercel Hobby<br/>React + Vite<br/>CDN tích hợp"]
    end

    subgraph application["Tầng ứng dụng"]
        backend["Render Free Web Service<br/>Spring Boot 3<br/>HTTPS công khai<br/>ngủ sau 15 phút không hoạt động"]
    end

    subgraph data["Dữ liệu và tệp"]
        database[("Supabase Free<br/>PostgreSQL<br/>500 MB database")]
        floorMaps[("Cloudflare R2 Free Tier<br/>Ảnh sơ đồ mặt bằng")]
    end

    email["Email API qua HTTPS<br/>(tùy chọn)"]

    user -- "HTTPS" --> frontend
    dns -. "phân giải tên miền" .-> frontend
    frontend -- "REST API / HTTPS<br/>JSON + JWT" --> backend
    backend -- "JDBC qua TLS<br/>Session Pooler" --> database
    backend -- "S3-compatible API / HTTPS" --> floorMaps
    frontend -. "tải ảnh qua URL công khai" .-> floorMaps
    backend -. "gửi email qua HTTPS" .-> email

    limitNote["Lưu ý môi trường Free-tier:<br/>- Render có thể ngủ sau 15p không có request (khởi động nguội 30-50s).<br/>- Scheduler áp dụng cơ chế catch-up khi thức dậy.<br/>- Docker Compose chỉ dùng cho local/test, không phải production deployment."]
    limitNote -.-> application

    classDef frontendLayer fill:#EFF6FF,stroke:#2563EB,stroke-width:1px,color:#0F172A;
    classDef backendLayer fill:#EEF2FF,stroke:#4F46E5,stroke-width:1px,color:#0F172A;
    classDef databaseLayer fill:#ECFDF5,stroke:#059669,stroke-width:1px,color:#0F172A;
    classDef storageLayer fill:#ECFEFF,stroke:#0891B2,stroke-width:1px,color:#0F172A;
    classDef edgeLayer fill:#FFF7ED,stroke:#EA580C,stroke-width:1px,color:#0F172A;
    classDef external fill:#FAFAFA,stroke:#64748B,stroke-width:1px,color:#0F172A;
    classDef note fill:#FAFAFA,stroke:#94A3B8,stroke-width:1px,stroke-dasharray:4 3,color:#475569;
    class frontend frontendLayer;
    class backend backendLayer;
    class database databaseLayer;
    class floorMaps storageLayer;
    class dns edgeLayer;
    class user,email external;
    class limitNote note;
```

---

## 3. Thành phần và Cấu trúc Mã nguồn (Package Structure)

Dự án áp dụng kiến trúc **Hexagonal / Clean Architecture** với cấu trúc module đề xuất:

```
com.alloca
├── domain                    <-- Core Domain Model (Java thuần, KHÔNG phụ thuộc Spring/JPA)
│   ├── booking               <-- Booking Entity, BookingStatus, TimeInterval VO
│   ├── resource              <-- Resource Entity, ResourceStatus, OperatingSchedule VO
│   ├── user                  <-- User Entity, Role, CredentialsVersion
│   ├── notification          <-- Notification Entity, NotificationType
│   └── alert                 <-- Alert Entity, AlertStatus, Episode
├── application               <-- Use Cases & Application Ports
│   ├── booking               <-- CreateBookingUseCase, CancelBookingUseCase
│   ├── resource              <-- ManageResourceUseCase, CheckAvailabilityUseCase
│   ├── auth                  <-- LoginUseCase, RefreshTokenUseCase
│   └── ports                 <-- In/Out Interfaces (Repositories, NotificationSender)
├── infrastructure            <-- Adapters & Framework Details
│   ├── persistence           <-- Spring Data JPA Entities & Repositories (PostgreSQL)
│   ├── security              <-- JwtTokenProvider, SecurityFilterChain, CurrentUserContext
│   ├── storage               <-- Cloudflare R2 S3 Client Adapter
│   ├── scheduler             <-- Spring Task Scheduler (Catch-up Jobs)
│   └── email                 <-- HTTPS Email API Client
└── web                       <-- HTTP Presentation Layer
    ├── controllers           <-- REST Controllers (@RestController)
    ├── dto                   <-- Request/Response DTOs & Validation Annotations
    └── exceptions            <-- GlobalExceptionHandler & ErrorResponse (RFC 7807)
```
