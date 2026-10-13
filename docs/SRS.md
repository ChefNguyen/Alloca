# Software Requirements Specification

## Alloca — Shared Resource Booking System

ĐẠI HỌC BÁCH KHOA – ĐHQG-HCM

KHOA KHOA HỌC VÀ KỸ THUẬT MÁY TÍNH

Đồ án chuyên ngành — Hệ thống đặt lịch và quản lý tài nguyên dùng chung cho SME.

Thành viên: Nguyễn Thanh Tân; Hồ Trọng Nghĩa; Nguyễn Hồng Huy.

GVHD: Trương Tuấn Anh.

Phiên bản 1.1 — ngày 11 tháng 10 năm 2026.

Bản nguồn chỉnh sửa: docs/SRS.md. Bản PDF được sinh từ bản nguồn này, không chỉnh trực tiếp PDF. Phiên bản 1.0 được giữ tại docs/archive/SRS.v1.0.original.pdf.

## Lịch sử thay đổi

- v1.0, 2026-10-05: yêu cầu ban đầu (PDF).
- v1.1, 2026-10-11: đồng bộ deployment theo Mermaid, thời gian booking, resource shared, đặt hộ, thống kê, job catch-up, an toàn tài khoản, floor map và kiểm thử. Giữ các mã FR/NFR/UC ban đầu; bổ sung mã mới, không tái sử dụng mã cũ cho nghiệp vụ khác.
- business-rules.md phân biệt quyết định đã chốt với mặc định triển khai mới; các mặc định là baseline để triển khai/kiểm thử, có thể điều chỉnh đồng bộ sau.

## Mục lục

1. Giới thiệu
2. Mô tả tổng thể và môi trường vận hành
3. Trường hợp sử dụng
4. Yêu cầu chức năng
5. Yêu cầu phi chức năng
6. Ràng buộc, giả định và giới hạn
7. Trạng thái, lỗi và truy vết

## 1. Giới thiệu

### 1.1 Mục đích

Tài liệu đặc tả hành vi phần mềm, phạm vi và tiêu chí nghiệm thu cho Alloca. Cấu trúc kế thừa SRS v1.0 tham khảo IEEE 830; không tuyên bố chứng nhận tuân thủ tiêu chuẩn. Dùng làm căn cứ thiết kế dữ liệu/API, acceptance testing và giải quyết khác biệt phạm vi.

### 1.2 Phạm vi

Alloca phục vụ một tổ chức duy nhất, cấp phát thời gian sử dụng phòng họp, hot desk và thiết bị dùng chung. Phần lõi gồm Auth/RBAC, Resource, Booking có chống xung đột, in-app Notification, Analytics/Alert và Floor Map. Thiết bị trong Phase 1 được quản lý như tài nguyên đặt theo giờ, chưa quản lý giao nhận, quá hạn trả hoặc tồn kho.

Email qua HTTPS API và export CSV là tính năng phụ ưu tiên P2, không phải điều kiện hoàn tất MVP bắt buộc. Priority P2 khác Phase 2. Ngoài phạm vi: vật tư tiêu hao, thanh toán, multi-tenant, SSO/Active Directory, calendar sync, AI, export PDF, check-in/IoT/NO_SHOW.

### 1.3 Thuật ngữ

- SRBS: Shared Resource Booking System; SME: doanh nghiệp vừa và nhỏ.
- RBAC: phân quyền theo role kết hợp phạm vi đối tượng ở backend.
- JWT: token ký số; không dùng HTTP session. Backend vẫn tra trạng thái/quyền hiện tại của user.
- Booking owner: user_id, người được đặt lịch; creator: created_by, người thực hiện thao tác.
- Slot: đơn vị bắt đầu/kết thúc theo lưới 30 phút. Availability có thể trả một khoảng trống gồm nhiều slot.
- Booking utilization: tỷ lệ thời gian đã đặt, không chứng minh mức sử dụng thực tế.
- Kỳ thời gian nửa mở [from,to): có from, không có to.
- Idempotent job: chạy lại cùng sự kiện không tạo kết quả nghiệp vụ trùng.

## 2. Mô tả tổng thể và môi trường vận hành

### 2.1 Bối cảnh và tích hợp

Single-tenant standalone application thay quy trình đăng ký thủ công. Admin nhập user, phòng ban và resource; không tích hợp HRM ở Phase 1. Người dùng truy cập frontend public HTTPS; mọi API nghiệp vụ yêu cầu xác thực/phân quyền. Không giả định mạng nội bộ/VPN là hàng rào bảo mật chính của bản demo.

### 2.2 Kiến trúc

React 18 + TypeScript + Vite; Java 17 + Spring Boot 3; Spring Security JWT/RBAC; Controller–Service–Repository; Spring Data JPA; PostgreSQL. Các service chính: Auth, Resource, Booking, Notification, Analytics, Floor Map. Spring Scheduler thực thi job khi backend hoạt động.

Booking concurrency dùng SELECT FOR UPDATE trên hàng resource trong PostgreSQL. Kiểm tra availability và insert cùng transaction; tất cả đường thay đổi trạng thái/giờ hoạt động liên quan phối hợp trên cùng lock. Không gọi dịch vụ email khi giữ lock.

### 2.3 Người dùng và phạm vi

- Employee: xem resource không DELETED, xem slot, tự đặt/hủy/xem booking của mình; xem notification của mình.
- Manager: kế thừa Employee; đặt hộ, hủy, xem booking của user cùng phòng ban hiện tại; báo cáo resource thuộc phòng ban; nhận alert department.
- Admin: quản trị user/department/zone/resource/schedule/map/settings; xem toàn hệ thống; đặt hộ/hủy theo cùng giới hạn thời gian.
- Resource department là sở hữu/quản lý, không phải danh sách người được đặt. Shared resource có department null; tất cả role có thể đặt khi hợp lệ.
- Scope booking dựa chủ booking, scope resource report dựa department sở hữu resource. Hai scope không được đánh đồng. Query filter chỉ thu hẹp, không mở rộng quyền.

### 2.4 Deployment chuẩn

Nguồn chuẩn: diagrams/deployment.mmd.

- Frontend: Vercel Hobby, React/Vite trên CDN.
- Backend: Render Free Web Service, Spring Boot/Java 17, HTTPS public; có cold start/ngủ khi không hoạt động theo giới hạn gói.
- Database: Supabase PostgreSQL, kết nối JDBC qua TLS và Session Pooler phù hợp driver/transaction.
- File: Cloudflare R2 qua S3-compatible API HTTPS; ảnh floor map không lưu trên filesystem tạm của Render.
- Email: HTTPS Email API tùy chọn; không yêu cầu SMTP nội bộ.
- DNS: Cloudflare tùy chọn khi có tên miền.
- Free-tier limits có thể thay đổi, cần kiểm tra nhà cung cấp trước triển khai. Không cam kết luôn miễn phí hoặc luôn sẵn sàng.
- Docker Compose/PostgreSQL container có thể dùng để phát triển/integration test, không phải deployment chuẩn của bản demo.

### 2.5 Demo và nghiệm thu

Free-tier chỉ bảo đảm logic đúng khi xử lý request/job; không hứa latency khi cold start hoặc reminder đúng giờ trong lúc backend ngủ. Môi trường nghiệm thu performance/scheduler phải always-on, ghi cấu hình, dataset và thời gian đo. Catch-up không thay thế SLA nhắc trước giờ bắt đầu. Đảm bảo không mất database/upload khi redeploy backend vẫn là yêu cầu ở mọi môi trường.

### 2.6 HTTP contract

Base path /api/v1. UUID identifiers; ISO8601 timestamps có timezone, response UTC, business date Asia/Ho_Chi_Minh. Pagination zero-based; size1–100. Lỗi gồm code máy đọc ổn định, message tiếng Việt, chi tiết validation nếu có. docs/api/openapi.yaml là contract thiết kế; SpringDoc khi có implementation phải khớp contract qua kiểm tra CI, không tự trở thành nguồn chân lý khác.

## 3. Trường hợp sử dụng

### 3.1 Danh sách

- UC-01 Đăng nhập — mọi role.
- UC-02 Xem resource — mọi role.
- UC-03 Lọc resource — mọi role.
- UC-04 Xem slot trống — mọi role.
- UC-05 Tạo booking của mình — mọi role.
- UC-06 Hủy booking của mình — mọi role, trước start.
- UC-07 Lịch sử booking — theo scope role.
- UC-08 Hủy booking người khác — Manager cùng department chủ booking, Admin toàn hệ thống; trước start.
- UC-09 Đặt hộ — Manager cùng department chủ booking, Admin toàn hệ thống.
- UC-10 Báo cáo department — Manager department của mình, Admin.
- UC-11 Quản lý resource — Admin.
- UC-12 Cấu hình giờ hoạt động — Admin, không làm invalid booking đã có.
- UC-13 Quản lý tài khoản — Admin, delete là deactivate.
- UC-14 Gán role/department — Admin.
- UC-15 Dashboard toàn hệ thống — Admin.
- UC-16 Ngưỡng cảnh báo resource — Admin.
- UC-17 Upload map và gán tọa độ resource — Admin.
- UC-18 Đặt qua map — mọi role, hai bước ngày và khoảng giờ.
- UC-19 Xem/đánh dấu đã đọc notification — chủ notification.
- UC-20 Cấu hình reminder lead time — Admin.
- UC-21 Đổi mật khẩu/hồ sơ cá nhân — người đăng nhập active.

### 3.2 UC-05 và UC-09 — Tạo booking

Điều kiện vào: actor active; owner active; actor được phép đặt cho owner; resource tồn tại. Request gồm resourceId, startTime, endTime, purpose tùy chọn; userId bỏ trống nghĩa là tự đặt.

1. Chọn resource/ngày/khoảng giờ, hoặc đi từ floor map.
2. Kiểm tra start/end đúng lưới, thời lượng, same-day, tương lai và horizon.
3. Bắt đầu transaction, khóa resource; kiểm tra lại status và operating windows.
4. Kiểm tra overlap với CONFIRMED trong cùng transaction.
5. Tạo booking CONFIRMED, audit/history và notification in-app durable (trực tiếp cùng transaction hoặc outbox).
6. Commit; trả201. Email tùy chọn xử lý sau commit, không làm mất booking đã thành công.

Ngoại lệ:401 account/token invalid;403 owner ngoài scope;400 thời gian không hợp lệ;404 resource không tồn tại/không được thấy;409 overlap/resource unavailable. Transaction thất bại không để lại booking/notification thành công. Hai booking cùng resource nhưng không overlap đều có thể được chấp nhận.

### 3.3 UC-06/08 — Hủy

Kiểm tra role và owner scope; chỉ CONFIRMED với now < start. Chuyển CANCELLED, ghi actor/reason/history, tạo notification cho owner và distinct creator, trả204. Tại start hoặc sau start từ chối cho mọi role. Không xóa record. Retry không tạo thêm transition/notification; response cho trạng thái đã hủy theo contract.

### 3.4 UC-07 — Lịch sử

Employee xem own booking; Manager xem theo department hiện tại của owner; Admin toàn hệ thống. Lọc thời gian/resource/status và pagination không vượt scope. Hệ thống không snapshot phòng ban cũ ở Phase1; chuyển department có thể làm thay đổi phạm vi lịch sử.

### 3.5 UC-11/12 — Resource và schedule

Admin tạo/sửa/suspend/reactivate/soft-delete resource. Suspend giữ booking cũ; delete chỉ khi SUSPENDED và không còn CONFIRMED có end > now. Schedule update kiểm tra mọi booking CONFIRMED chưa kết thúc; nếu lịch mới không chứa trọn booking thì trả409 cùng danh sách xung đột, giữ lịch cũ nguyên vẹn. Admin xử lý booking trước; không tự hủy. Cùng khóa resource như create booking để chống race.

## 4. Yêu cầu chức năng

### 4.1 Auth và RBAC

- FR-AUTH-01: đăng nhập username/password, trả access token30 phút và refresh token7 ngày.
- FR-AUTH-02: token invalid/expired trả401; refresh endpoint kiểm tra chữ ký/type/expiry/active/credentials version trước cấp access mới.
- FR-AUTH-03: EMPLOYEE/MANAGER/ADMIN; endpoint role guard và service object-scope guard. Sai quyền trả403, không dựa riêng UI.
- FR-AUTH-04: Admin tạo/sửa/disable/delete account; delete là deactivate, giữ FK/history. Mỗi protected request từ account disabled bị từ chối dù access token còn hạn.
- FR-AUTH-05: Admin gán role/department; backend dùng quyền hiện tại. Booking Manager scope là department chủ booking; không mở rộng bằng userId query.
- FR-AUTH-06: đổi mật khẩu làm mọi refresh token trước đó mất hiệu lực. credentials_version tăng cùng transaction đổi hash; claim version cũ bị từ chối. Không hứa revoke access token active còn hạn do password change riêng lẻ.
- FR-AUTH-07: logout client xóa token; không có server-side per-session revocation trong Phase1. Token bị sao chép có thể còn hợp lệ đến hạn theo policy active/version.
- FR-AUTH-08: user xem/sửa hồ sơ cho phép và đổi mật khẩu; không tự đổi role, department hoặc isActive.

### 4.2 Resource Management

- FR-RES-01: Admin CRUD metadata tên, loại ROOM/EQUIPMENT/DESK, capacity/thông số qua mô tả, status; không quản lý thiết bị tiêu hao/giao nhận.
- FR-RES-02: lịch tuần theo resource, nhiều window không overlap, start < end; không window nghĩa là đóng. Booking ngoài hợp window liên tục bị từ chối.
- FR-RES-03: gắn resource với zone/tầng/tòa nhà, department optional; department null là shared và alert gửi Admin.
- FR-RES-04: mọi user active xem resource không DELETED, chi tiết/lọc type/zone/status. Department sở hữu không hạn chế ai có thể đặt.
- FR-RES-05: delete là soft-delete chỉ từ SUSPENDED khi không có CONFIRMED end > now. Suspend không tự hủy booking.
- FR-RES-06: chặn schedule update làm invalid CONFIRMED chưa kết thúc; trả booking conflicts, không partial update.
- FR-RES-07: Admin quản lý zone/department; chặn xóa khi còn tham chiếu, không cascade xóa dữ liệu booking/audit.

### 4.3 Booking Engine

- FR-BOOK-01: availability theo ngày địa phương, resource state, giờ hoạt động, lưới30 phút, current time/horizon và CONFIRMED overlaps.
- FR-BOOK-02: create booking với start/end theo bước30 phút, 30 phút–8 giờ, cùng ngày; start > now và <=now+30 ngày, không trùng. Không sửa booking sau tạo ở Phase1.
- FR-BOOK-03: SELECT FOR UPDATE resource trước kiểm tra/insert trong transaction; overlap trả409. Request cùng resource khác khoảng không bị quy tắc chỉ-một chặn sai.
- FR-BOOK-04: owner hủy own; Manager hủy cùng owner department; Admin mọi booking. Tất cả chỉ trước start và trạng thái CONFIRMED.
- FR-BOOK-05: lịch sử theo scope, lọc/pagination; snapshot status history không bị xóa khi deactivate user/resource.
- FR-BOOK-06: trạng thái CONFIRMED/CANCELLED/COMPLETED. Job chuyển CONFIRMED end<=now thành COMPLETED một lần; actor hệ thống nullable. NO_SHOW/check-in ngoài Phase1.
- FR-BOOK-07: userId optional để đặt hộ; creator lấy từ principal, không nhận giả mạo từ request. Owner nhận thông báo và quyết định scope Manager.

### 4.4 Notification và scheduler

- FR-NOTIF-01: confirmation in-app cho owner gồm resource/time/status. Booking commit phải có durable notification hoặc durable outbox; không mất event do backend chết sau commit.
- FR-NOTIF-02: reminder cho owner; lead time configurable mặc định15 phút. Không nhắc hồi tố booking tạo sau due. Job catch-up đủ điều kiện theo business-rules.md BR-JOB-01.
- FR-NOTIF-03: cancellation thông báo owner và creator nếu khác owner; deduplicate recipient; gồm actor, reason nếu có.
- FR-NOTIF-04: alert department resource gửi Manager active department đó; shared gửi Admin active. Không có Manager thì alert vẫn lưu để Admin xem.
- FR-NOTIF-05: P2 optional email qua HTTPS Email API, không SMTP; lỗi kênh phụ không rollback booking hoặc in-app notification.
- FR-NOTIF-06: list notification và mark-read theo chính người nhận; không cho đọc/sửa notification user khác.
- FR-NOTIF-07: Admin cấu hình reminder lead time durable; default15, range1–1440 phút là mặc định triển khai. Không gửi lại notification đã gửi sau đổi settings.
- FR-JOB-01: catch-up khi wake: completion overdue, reminder trễ tối đa5 phút và chưa start, latest alert evaluation. Không replay tất cả alert từng ngày ngủ.
- FR-JOB-02: job/notification idempotent dưới retry/concurrency; unique event/recipient và atomic state update. Ghi lý do skip và kết quả vận hành, không hứa exactly-once email bên ngoài.

### 4.5 Analytics và Alerting

- FR-STAT-01: report tuần/tháng, CONFIRMED+COMPLETED clipped to period, exclude CANCELLED; denominator operating windows. Rate là phần trăm thời gian đã đặt; không actual occupancy.
- FR-STAT-02: booking trend theo ngày startTime sử dụng trong kỳ, nhóm status, rõ timezone và ý nghĩa trục. Không đánh đồng với count theo createdAt.
- FR-STAT-03: daily alert trên7 ngày trọn vẹn trước ngày chạy job; default85% override resource; vượt là >, không >=. Một active episode/resource/type, không spam khi vẫn vượt; <= threshold resolve; vượt lại tạo episode mới.
- FR-STAT-04: Admin dashboard mọi resource, trend và alert list. Scope/filter/pagination rõ ràng, không yêu cầu triển khai chart trong tài liệu này.
- FR-STAT-05: Manager report theo resource department mình sở hữu, tính mọi booking trên resource đó; không được xem report department khác/shared toàn công ty nếu chưa có quyền Admin.
- FR-STAT-06: P2 optional CSV export đúng scope; không export PDF Phase1.
- FR-STAT-07: denominator0 -> rate null/N/A và không đánh giá threshold. Lịch hiện tại được dùng cho mẫu số quá khứ; không có lịch sử schedule trong MVP, không clamp tỷ lệ >100 nếu cấu hình đã thay đổi. Phải công bố giới hạn.
- FR-STAT-08: alert ACKNOWLEDGED còn active, không mở duplicate; resolve khi phục hồi. Resource unavailable được resolve với lý do, không tạo alert mới; job trễ chỉ đánh giá kỳ hiện tại.

### 4.6 Floor Map

- FR-MAP-01: Admin upload PNG/JPEG theo zone, mặc định max10 MiB; kiểm tra nội dung, upload R2, không dùng filesystem Render làm storage lâu dài. SVG chỉ là overlay UI.
- FR-MAP-02: Admin gán x,y,width,height theo pixel ảnh gốc; trong biên ảnh và kích thước dương. Trả kích thước ảnh để UI scale; thay ảnh kiểm tra mapping cũ.
- FR-MAP-03: bước ngày: xanh tất cả slot hợp lệ trống, vàng một phần, đỏ không có; SUSPENDED xám; DELETED ẩn non-admin. Sau chọn range: xanh nếu đủ toàn khoảng, đỏ nếu không; không dùng vàng cho khoảng invalid. Kèm text không chỉ màu.
- FR-MAP-04: click resource trống mở form prefill; submit vẫn dùng Booking Engine và có thể409 do người khác vừa đặt. Không giữ slot bằng việc xem map.

## 5. Yêu cầu phi chức năng

### 5.1 Hiệu năng

- NFR-PERF-01: create booking dưới1 giây ở môi trường nghiệm thu warmed-up, dưới50 request đang đồng thời; ghi dataset/cấu hình/kết quả theo test-plan.md. Không áp SLA này cho free-tier cold start.
- NFR-PERF-02: API list resource/availability dưới500ms với điều kiện nghiệm thu tương tự; kiểm tra pagination và giới hạn query.
- NFR-PERF-03: 20 create request đồng thời cùng resource và cùng interval xung đột, ban đầu trống -> đúng một201, còn lại409; database đúng một record. Nếu interval không overlap, các request hợp lệ đều có thể thành công.

### 5.2 Bảo mật

- NFR-SEC-01: BCrypt cost >=12; không lưu/log password/token thô.
- NFR-SEC-02: JWT HS256 secret entropy tối thiểu256bit trong environment secrets; validate type, chữ ký, expiry, credentials version cho refresh.
- NFR-SEC-03: authorization tại API và service/object scope; active/current role/department từ dữ liệu hiện tại; không tin ownerId hoặc role client.
- NFR-SEC-04: HTTPS TLS1.2+ frontend/backend/email/storage; database TLS. CORS allowlist frontend, không wildcard credentialed origins.
- NFR-SEC-05: validate input, parameterized DB queries, context-aware output escaping; upload allowlist nội dung/kích thước. Không coi sanitize chuỗi là thay thế prepared queries/XSS encoding.

### 5.3 Độ tin cậy

- NFR-REL-01: ACID booking, không hai CONFIRMED overlap trên cùng resource. Lock/transaction là authoritative, không dựa UI availability.
- NFR-REL-02: Supabase giữ dữ liệu qua redeploy backend; R2 giữ upload. Không dùng ephemeral disk Render cho dữ liệu nghiệp vụ. Backup/restore được kiểm tra bằng dữ liệu test; free-tier không là cam kết backup SLA.
- NFR-REL-03: job restart/retry không tạo duplicate state history, alert episode hoặc notification; sleep có thể làm job trễ/bỏ reminder ngoài catch-up window theo policy, phải hiển thị giới hạn demo.

### 5.4 Bảo trì

- NFR-MAIN-01: Controller–Service–Repository; nghiệp vụ ở Service, không DB direct từ Controller.
- NFR-MAIN-02: OpenAPI thiết kế và SpringDoc implementation được đối chiếu trong CI; endpoint/version/schema không được drift âm thầm.
- NFR-MAIN-03: unit-test service coverage>=70%; bổ sung integration PostgreSQL thật cho row lock/rollback/scope, không dùng coverage thay chứng minh concurrency. Test quan trọng cùng sprint với booking.

### 5.5 Mở rộng

- NFR-SCALE-01: REST API cho client mới không viết lại nghiệp vụ.
- NFR-SCALE-02: nhiều instance cùng PostgreSQL vẫn dùng row lock hiện có. Không bắt buộc Redis distributed lock cho Booking Engine. Khi scale phải phối hợp scheduler/durable jobs và bảo vệ idempotency, không nhân đôi reminder/alert.

### 5.6 Usability

- NFR-USE-01: đặt trong tối đa3 bước chính từ màn hình chính: resource -> time -> confirm; floor map chọn ngày/range trong bước resource/time, không thêm quy trình phê duyệt.
- NFR-USE-02: lỗi có lý do và hướng xử lý, ví dụ chọn slot khác hoặc xử lý booking đang chặn schedule update.
- NFR-USE-03: desktop1280px+ và tablet768px+; map scale đúng tọa độ, legend có text trạng thái.

## 6. Ràng buộc, giả định và giới hạn

- Single organization, nội bộ username/password; không SSO/OAuth integration Phase1.
- Quy mô mục tiêu10–200 user hoạt động; điều này khác số request đang xử lý đồng thời. NFR performance có workload kiểm thử riêng, không suy diễn capacity200 concurrent requests từ số user.
- Admin có kỹ năng cấu hình cơ bản; user/department nhập tay; mạng ổn định ở môi trường nghiệm thu.
- Cấu hình deployment miễn phí phục vụ demo, có thể ngủ hoặc giới hạn database. Giá/hạn mức thay đổi theo provider, kiểm tra lại trước triển khai.
- Lịch sử department/schedule không version trong MVP; scope/report theo cấu hình hiện tại, phải công bố. Nếu cần báo cáo lịch sử bất biến, thêm effective-dated snapshots ở giai đoạn sau.
- Tất cả ngày/giờ dùng Asia/Ho_Chi_Minh cho nghiệp vụ và UTC lưu/trao đổi; server clock quyết định due và timeout.
- Reminder trễ quá5 phút hoặc đến sau start không gửi; booking late-created không gửi reminder hồi tố. Đây là mặc định triển khai đi cùng lựa chọn catch-up, không cam kết reminder đúng giờ trên sleeping service.
- Không tự hủy booking khi disable user, chuyển department, suspend resource hoặc sửa schedule. Schedule invalid bị chặn; Admin xử lý theo policy hủy trước start.

## 7. Trạng thái, lỗi và truy vết

### 7.1 Booking

CONFIRMED sau create; CANCELLED do actor hợp lệ trước start; COMPLETED do hệ thống khi end<=now. History actor null cho SYSTEM. Không xóa record booking; NO_SHOW và checkInAt chỉ dự phòng Phase2.

### 7.2 Resource

AVAILABLE <-> SUSPENDED; SUSPENDED -> DELETED nếu không có CONFIRMED chưa kết thúc. Không AVAILABLE -> DELETED. DELETED giữ history, không hiển thị non-admin.

### 7.3 HTTP

200 đọc/cập nhật;201 tạo;204 hủy/xóa mềm/mark-read theo endpoint;400 validation;401 auth/account invalid;403 role/scope denied;404 không tồn tại/không được nhìn thấy theo contract;409 xung đột booking/schedule/state;413 upload quá giới hạn;415 sai media type;500 lỗi không dự kiến. Error code cụ thể và payload theo api/openapi.yaml.

### 7.4 Tài liệu đối chiếu

- business-rules.md: các quy tắc BR và boundary chính xác.
- api/openapi.yaml + api/README.md: HTTP contract và examples.
- diagrams/README.md: sơ đồ kiến trúc, state/sequence/flow.
- test-plan.md: test IDs, kết quả kỳ vọng, traceability FR/Jira.
- ../Backlog.xlsx + backlog/: acceptance criteria từng issue và local tasks chưa có Jira key.
- archive/SRS.v1.0.original.pdf: baseline cũ; không dùng làm hướng dẫn triển khai sau v1.1.
