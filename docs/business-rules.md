# Alloca — Business rules Phase 1

Phiên bản 1.1 — 2026-10-11. Tài liệu nguồn cho SRS, API, sơ đồ và acceptance criteria.

## 1. Nguồn chuẩn và trạng thái quyết định

- Deployment: `architecture/deployment.mmd` — Vercel, Render Free, Supabase PostgreSQL, Cloudflare R2, Email API HTTPS tùy chọn. Docker Compose chỉ có thể dùng local/test, không phải deployment chuẩn.
- Nghiệp vụ: tài liệu này và `SRS.md`; HTTP contract: `api/openapi.yaml`; kiểm thử: `test-plan.md`.
- Các đặc tả module hóa chi tiết (kèm sơ đồ Mermaid & test checklist) tại `specs/`:
  - Booking & Time: [`specs/booking-and-time.md`](specs/booking-and-time.md)
  - Resources & Schedules: [`specs/resources-and-schedules.md`](specs/resources-and-schedules.md)
  - RBAC & Security: [`specs/rbac-and-security.md`](specs/rbac-and-security.md)
  - Notifications & Jobs: [`specs/notifications-and-jobs.md`](specs/notifications-and-jobs.md)
  - Utilization & Alerts: [`specs/utilization-and-alerts.md`](specs/utilization-and-alerts.md)
  - Floor Map: [`specs/floor-map.md`](specs/floor-map.md)
- Quy tắc ghi **Đã chốt** phản ánh lựa chọn của người dùng. Quy tắc ghi **Mặc định triển khai** làm rõ biên còn thiếu, không phải lựa chọn đã được người dùng bỏ phiếu riêng. Thay đổi các mặc định phải đồng bộ contract và test.
- Phase 1 bắt buộc: Auth/RBAC, Resource, Booking, in-app Notification, Analytics/Alert, Floor Map. Email và CSV là ưu tiên phụ (P2 priority), không đồng nghĩa Phase 2. PDF export, SSO, calendar sync, multi-tenant, check-in/NO_SHOW thuộc ngoài phạm vi.

## 2. Booking và thời gian

### BR-TIME-01 — Đã chốt

- Business timezone `Asia/Ho_Chi_Minh`; timestamp API có offset và response chuẩn hóa UTC.
- Bắt đầu/kết thúc theo bước 30 phút; thời lượng tối thiểu 30 phút, tối đa 8 giờ; không qua ngày địa phương.
- Cho đặt tối đa 30 ngày trước; không tạo booking trong quá khứ.
- Khoảng phải nằm trọn trong operating windows, resource `AVAILABLE`, không trùng booking `CONFIRMED`.
- Khoảng nửa mở `[start,end)`: giao nhau khi `start < existing.end AND end > existing.start`; hai booking tiếp giáp được phép.

### BR-TIME-02 — Mặc định triển khai

- Minute của start/end là 00 hoặc 30, seconds/fraction là 0. `start > serverNow`; `start <= serverNow + 30 days`; kiểm tra trên server, không tin đồng hồ client. Điều kiện same-day áp dụng cho cả hai mốc.
- Schedule theo tuần, các window trong cùng ngày, `start < end`, không overlap; window tiếp giáp có thể hợp nhất để kiểm tra containment. Không có window cho ngày đó nghĩa là đóng cửa. Không thêm holiday calendar trong MVP.
- Schedule có thể dùng phút bất kỳ; booking vẫn phải theo lưới 30 phút và nằm trong hợp các window liên tục.
- Availability và map ngày chỉ xét slot còn đặt được: bỏ thời gian đã qua; ngày ngoài horizon không có slot hợp lệ. Booking chính thức luôn kiểm tra lại, không bảo đảm giữ slot từ lúc xem.
- Không hỗ trợ sửa thời gian/resource của booking đã tạo trong Phase 1; hủy và tạo booking mới theo cùng quy tắc.

## 3. Quyền và resource

### BR-SCOPE-01 — Đã chốt

- Employee chỉ tạo/xem/hủy booking của mình. Manager được thao tác booking của người thuộc department của mình. Admin thao tác toàn hệ thống.
- Booking scope dựa vào department của **chủ booking** (`user_id`), không phải người tạo (`created_by`) hoặc resource.
- Resource có department sở hữu hoặc `department_id = null` (shared). Department resource xác định phạm vi báo cáo/alert, không tự tạo hạn chế đặt resource cho người khác department.
- Shared resource alert gửi Admin; resource thuộc department gửi các Manager phụ trách department đó.

### BR-SCOPE-02 — Mặc định triển khai

- Quyền lấy theo role/department hiện tại của user; lịch sử booking không snapshot department trong MVP. Chuyển department có thể đổi phạm vi lịch sử nhìn thấy; ghi audit việc đổi này.
- Disable/chuyển department không tự hủy booking. Người tạo không có thêm quyền đọc booking sau khi mất department scope chỉ vì từng đặt hộ; notification vẫn là dữ liệu của người nhận, không mở rộng quyền truy cập booking.
- Người được đặt hộ phải là tài khoản active. Các recipient thông báo phải active; nếu department không có Manager active thì alert vẫn được lưu và Admin xem được trên dashboard, không phát sinh broadcast ngoài scope.
- Manager không có department không được truy cập booking người khác hoặc báo cáo department; luôn còn quyền tự phục vụ của Employee.

### BR-RESOURCE-01 — Đã chốt

- `AVAILABLE`: cho booking mới. `SUSPENDED`: chặn booking mới, giữ booking cũ. `DELETED`: soft-delete, ẩn với người dùng cuối, giữ dữ liệu lịch sử.
- Delete chỉ từ `SUSPENDED` và không còn booking `CONFIRMED` chưa kết thúc. Không cho delete từ `AVAILABLE`.
- Sửa operating schedule phải bị từ chối nếu làm booking `CONFIRMED` tương lai không hợp lệ; phản hồi liệt kê booking xung đột để Admin xử lý, không tự hủy.

### BR-RESOURCE-02 — Mặc định triển khai

- Bảo vệ cả booking đang diễn ra (`end > now`), không chỉ booking chưa bắt đầu, khi đổi schedule/delete.
- Create booking, thay schedule, thay status/delete cùng khóa resource trong transaction để tránh kiểm tra trên state cũ. Không gửi email trong transaction giữ lock.
- Hủy booking chỉ khi `CONFIRMED` và `now < start` cho tất cả role; quá hạn kể cả Admin không được hủy. Bảo trì khẩn cấp khi booking đang diễn ra không có luồng force-cancel trong MVP.
- Soft-delete user là deactivate để bảo toàn FK. Delete department/zone bị chặn khi còn tham chiếu cần bảo toàn; không cascade xóa lịch sử.

## 4. Notification và job nền

### BR-NOTIF-01 — Đã chốt

| Sự kiện | Recipient |
| --- | --- |
| Tạo booking | Chủ booking |
| Reminder | Chủ booking |
| Cancellation | Chủ booking và người tạo nếu khác chủ booking; không trùng recipient |
| Alert resource department | Manager active của department sở hữu resource |
| Alert shared | Admin active |

- In-app bắt buộc; Email API HTTPS là kênh phụ, lỗi email không rollback booking.
- Reminder lead time configurable, mặc định 15 phút. Booking phút chót vẫn được tạo; nếu tạo sau thời điểm reminder thì không gửi reminder hồi tố.
- Có job completion, reminder, alert; idempotent và catch-up khi backend hoạt động lại. Free-tier không bảo đảm chạy đúng giờ lúc service ngủ.

### BR-JOB-01 — Mặc định triển khai

- Lead time toàn hệ thống do Admin cấu hình (1–1440 phút); lưu durable trong database, không trong filesystem Render. Thay đổi áp dụng cho booking chưa được nhắc, không gửi lại booking đã nhắc.
- `due = start - configuredLeadTime`. Reminder chỉ đủ điều kiện khi `createdAt <= due`, `due <= now < start`, `now - due <= 5 minutes`, trạng thái còn `CONFIRMED`, chưa có reminder đã gửi.
- Booking tạo sau due không được nhắc bù. Catch-up chỉ dành cho booking vốn đủ điều kiện nhưng job trễ. Ví dụ start10:00, due09:45: tạo09:30, wake09:48 -> gửi; tạo09:50 -> không gửi; wake09:51 -> không gửi.
- Mốc đúng due vẫn hợp lệ; trễ đúng 5 phút còn hợp lệ; tại start không gửi. Đánh dấu đã nhắc cùng transaction tạo notification; unique event/recipient key chống trùng khi retry.
- Completion cập nhật mọi `CONFIRMED` có `end <= now` thành `COMPLETED`, tạo một history row với `changed_by = null` (SYSTEM); không chuyển `CANCELLED`.
- Khi wake/restart: catch-up completion, reminder hợp lệ và một lần đánh giá alert cho kỳ hiện tại. Không replay từng ngày alert cũ. Job định kỳ gợi ý: reminder/completion mỗi phút, alert 00:05 giờ địa phương; startup cũng catch-up.
- Confirmation/cancellation notification được ghi durable với thay đổi booking trong cùng transaction, hoặc qua durable outbox ghi trong transaction; email chỉ sau commit. Không dựa vào event in-memory sau commit mà không có cơ chế phục hồi. Mỗi event/recipient chỉ một in-app notification.
- Notification chỉ list/mark-read của chính người đăng nhập. Việc đọc notification không cấp quyền xem booking ngoài scope.

## 5. Booking utilization và alert

### BR-STAT-01 — Đã chốt

- Báo cáo tuần/tháng; timezone địa phương. Thuật ngữ UI: **tỷ lệ thời gian đã đặt**, không phải mức sử dụng thực tế.
- Tử số: tổng thời gian booking `CONFIRMED` và `COMPLETED` giao với kỳ báo cáo; loại `CANCELLED`. Hoàn thành booking không làm giảm tỷ lệ.
- Mẫu số: tổng thời gian operating windows trong kỳ. `rate = bookedMinutes / availableMinutes * 100`.
- Alert chạy mỗi ngày theo 7 ngày gần nhất; threshold mặc định 85%, Admin override từng resource; routing theo BR-SCOPE-01.

### BR-STAT-02 — Mặc định triển khai

- Tuần: thứ Hai 00:00 đến thứ Hai sau; tháng: ngày 1 đến ngày 1 tháng sau. Endpoint có thể nhận khoảng rõ ràng, dùng `[from,to)` sau chuyển UTC. Báo cáo kỳ hiện tại tính toàn kỳ, gồm cả booking tương lai đã xác nhận và toàn bộ lịch hoạt động trong kỳ.
- Kỳ alert: `[00:00 hôm nay - 7 ngày, 00:00 hôm nay)` theo Asia/Ho_Chi_Minh; đó là rolling 7 ngày đã hoàn tất, không tính phần ngày chạy job.
- Chỉ alert khi `rate > threshold`; đúng 85% với threshold85 không alert. Threshold range 0–100. Không có mẫu số thì rate `null`/N/A, không phát hoặc đóng alert dựa trên dữ liệu không xác định.
- Mẫu số dựa trên lịch tuần **hiện được cấu hình**; MVP không có version lịch/suspend theo lịch sử. Suspend không xóa giờ khỏi mẫu số; thay lịch có thể tính lại tỷ lệ quá khứ. Công bố giới hạn này trên báo cáo. Không clamp tỷ lệ >100 khi lịch cũ khác lịch hiện tại; vẫn trả phút gốc và metadata/cảnh báo giới hạn dữ liệu.
- Không phát alert mới cho resource `DELETED`/`SUSPENDED`; alert còn mở được resolve với lý do resource unavailable. Báo cáo vẫn có thể bao gồm resource đã suspend/delete trong lịch sử theo scope.
- Mỗi resource/type có tối đa một alert active (OPEN hoặc ACKNOWLEDGED). Vượt ngưỡng khi chưa active -> tạo và notify; vẫn vượt -> cập nhật số liệu/kỳ, không spam; xuống <=threshold -> RESOLVED; lần vượt tiếp sau tạo episode mới. ACK không đồng nghĩa hết tình trạng vượt ngưỡng.
- Admin xem toàn hệ thống. Báo cáo utilization department dựa trên department **sở hữu resource** và tính mọi booking trên các resource đó. Không dùng phạm vi này thay cho scope booking owner của Manager.
- Booking trend đếm booking theo ngày bắt đầu sử dụng trong kỳ, phân nhóm status; không gọi đây là lượt tạo request theo createdAt. Dashboard phải ghi rõ trục và ý nghĩa.

## 6. Floor map

### BR-MAP-01 — Đã chốt

- Hai bước: xem ngày -> chọn start/end -> đánh giá lại khoảng cụ thể -> xác nhận booking.
- Tải PNG/JPEG, SVG chỉ là overlay hiển thị clickable, không phải định dạng upload tùy ý.

### BR-MAP-02 — Mặc định triển khai

- Ngày: xanh khi mọi slot hợp lệ còn trống; vàng khi còn một phần slot; đỏ khi không còn slot hợp lệ. Một slot hợp lệ dài ít nhất30 phút theo BR-TIME. Ngày đóng cửa hoặc chỉ còn thời gian đã qua không được tô xanh.
- Khoảng đã chọn: xanh nếu toàn bộ khoảng đặt được; đỏ nếu không đặt được; không dùng vàng để gợi ý rằng booking xung đột có thể được chấp nhận.
- Resource SUSPENDED: xám, không clickable booking; DELETED: không hiển thị cho non-admin. Không dùng màu làm tín hiệu duy nhất: kèm label/legend.
- Tọa độ dùng pixel của ảnh gốc, có kích thước ảnh để scale trên UI; tọa độ/width/height nằm trong ảnh, width/height dương. Thay ảnh phải kiểm tra hoặc yêu cầu đặt lại vị trí không còn hợp lệ.
- Giới hạn upload mặc định 10 MiB, PNG/JPEG thực tế qua kiểm tra nội dung; URL ảnh R2 có thể công khai theo deployment, không nhúng thông tin nhạy cảm vào map.

## 7. Account security

### BR-AUTH-01 — Đã chốt

- Access token 30 phút, refresh token7 ngày. Mỗi request protected kiểm tra active; deactivate có hiệu lực dù access token cũ còn hạn.
- Đổi mật khẩu vô hiệu mọi refresh token trước đó. Logout Phase1 chỉ xóa token phía client, không hứa vô hiệu token bị sao chép.

### BR-AUTH-02 — Mặc định triển khai

- Dùng `credentials_version` monotonic trong user và claim refresh token. Đổi/reset mật khẩu cập nhật hash và tăng version trong một transaction; refresh yêu cầu version khớp, account active, đúng loại token, chữ ký và hạn.
- Refresh không phải auth session store. Đổi mật khẩu không cam kết thu hồi access token còn hạn; role/department/active vẫn được kiểm tra theo DB hiện tại. Có thể tăng credentials_version khi deactivate để tránh refresh cũ sống lại sau reactivation.
- Logout server-side từng phiên, refresh rotation/reuse detection thuộc hướng mở rộng, không là acceptance criterion Phase1.

## 8. Nghiệm thu và giới hạn demo

- Free-tier không bảo đảm SLA hoặc reminder đúng giờ khi ngủ; báo rõ trong README/demo. Môi trường kiểm thử luôn chạy dùng để nghiệm thu scheduler và latency; không thay đổi lựa chọn cloud demo.
- Correctness không được giảm theo môi trường: không trùng booking, không vượt scope, không tạo trùng notification vì retry.
- Test phải có PostgreSQL integration cho transaction/row lock, không chỉ mock repository. Test thời gian dùng clock kiểm soát được.
- Tham chiếu `test-plan.md` và `backlog/README.md`. Không coi chỉnh tài liệu là test ứng dụng đã chạy.
