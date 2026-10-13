# Alloca — Acceptance và integration test plan

Version1.1 — 2026-10-11. Đây là kế hoạch kiểm thử, **chưa phải kết quả chạy ứng dụng**.

## Môi trường và fixture

- PostgreSQL thật (có thể Testcontainers); không dùng H2/mock để chứng minh SELECT FOR UPDATE. Schema/migrations giống deployment.
- Server clock tiêm được; Asia/Ho_Chi_Minh, UTC response. Fixture thời gian ngày2030-06-10 (Monday), now07:00+07:00 trừ khi case override; tránh fixture hết hạn vì ngày thực tế.
- Hai department A/B; employee A1/A2/B1, Manager MA/MB, Admin AD; account inactive; room RA ownedA, RB ownedB, RS shared. Tất cả AVAILABLE, schedule Mon–Fri08:00–17:00, threshold85 trừ khi override.
- Notification fixture phân biệt owner/creator; chỉ dùng dữ liệu giả. Không gọi Email API thật trong test mặc định.
- Mỗi case isolated; lưu request/result/DB assertions. Negative scope test dùng ID đối tượng thật để không nhầm403/404 với record không tồn tại.

## 1. Thời gian — FR-BOOK-01/02, FR-RES-02; ALC-30/34/35

| ID | Input/tình huống | Kết quả kỳ vọng |
| --- | --- | --- |
| TC-TIME-01 | 09:00–09:30; 08:00–16:00; 09:15–09:45; 09:00–09:00;09:00–17:30 | Hai đầu hợp lệ; lệch lưới, zero duration, >8h bị400 |
| TC-TIME-02 | start trong quá khứ/đúng now; >now; đúng now+30days; vượt1giây | Chỉ start>now và <=horizon được phép nếu các điều kiện khác đúng; dùng now tròn30phút cho boundary |
| TC-TIME-03 | Qua ngày23:30–00:30; ngoài operating windows; ngày đóng; cắt qua giờ nghỉ |400, không booking; window liền nhau có thể hợp nhất |
| TC-TIME-04 | Tồn tại09:00–10:00; tạo10:00–10:30 và09:30–10:30 | Tiếp giáp201, overlap409 |
| TC-TIME-05 | Cùng instant gửi dạng+07:00 và Z; response/readback | Cùng kết quả nghiệp vụ; response UTC; kiểm tra day địa phương không phải day UTC |
| TC-TIME-06 | now09:20; availability hôm nay; resource SUSPENDED; ngoài30days | Không trả slot đã qua/không hợp lệ; SUSPENDED không bookable; create luôn revalidate |

## 2. Transaction/concurrency — FR-BOOK-03, NFR-REL-01/PERF-03; ALC-36/63

| ID | Input/tình huống | Kết quả kỳ vọng |
| --- | --- | --- |
| TC-CONC-01 | Barrier đồng bộ20 transaction tạo cùng resource09:00–10:00, ban đầu trống | Đúng một201,19 phản hồi409; DB một booking CONFIRMED; notification chỉ cho booking thắng |
| TC-CONC-02 | Hai request cùng resource09:00–10:00 và10:00–11:00 | Cả hai201 sau chờ lock nếu cần; không áp sai quy tắc chỉ-một |
| TC-CONC-03 | Fault injection trước commit sau insert booking/notification | Không có booking/history/notification orphan; rollback toàn bộ; outbox nếu dùng cũng rollback |
| TC-CONC-04 | Race create với suspend, schedule replacement hoặc delete | Serializable theo resource lock: kết quả cuối không tạo booking từ resource state/schedule cũ đã bị đổi; có thể một thao tác bị409 |
| TC-CONC-05 | Hai consumer/job chạy cùng event; crash sau commit trước dispatch | Durable recovery tạo đúng một in-app event/recipient; không mất confirmation; không cam kết email exactly-once |

## 3. Scope — FR-AUTH-03/05, FR-BOOK-04/05/07; ALC-25/27/39/40/41

| ID | Input/tình huống | Kết quả kỳ vọng |
| --- | --- | --- |
| TC-SCOPE-01 | Employee A1 gửi userId=A2; MA đặt cho A1/B1; AD đặt cho B1 | A1 bị403; MA->A1 được, MA->B1 bị403; AD được; createdBy luôn principal |
| TC-SCOPE-02 | MA list/history/cancel booking B1 bằng direct ID và query departmentB | Không đọc/sửa ngoài scope; không dựa UI filter |
| TC-SCOPE-03 | A1 đặt resource RA/RB/RS; MA report RA/RB/RS | Resource ownership không cấm A1 đặt; MA report chỉ resource departmentA, không toàn bộ shared/B |
| TC-SCOPE-04 | Chuyển A1 sangB; MA xem booking A1 cũ; creator cũ click notification | Scope theo department hiện tại; notification không cấp quyền ngoài scope; audit thay đổi giữ nguyên |

## 4. Resource/cancellation — FR-RES-05/06, FR-BOOK-04; ALC-29/30/38/39/40

| ID | Input/tình huống | Kết quả kỳ vọng |
| --- | --- | --- |
| TC-RESOURCE-01 | Có booking09:00–11:00; đổi schedule13:00–17:00 |409 danh sách conflict, schedule cũ nguyên vẹn, không tự hủy |
| TC-RESOURCE-02 | Suspend RA đang có booking tương lai; delete; hủy trước start rồi delete | Suspend giữ booking; delete bị409; sau hủy hợp lệ delete thành công soft-delete; AVAILABLE delete bị409 |
| TC-RESOURCE-03 | Booking đang diễn ra; Admin cancel/delete hoặc schedule làm invalid | Không force-cancel; delete/schedule bị chặn; history không mất |
| TC-RESOURCE-04 | Owner/MA/AD hủy trước start, tại start, sau start; retry cancellation | Chỉ trước start và CONFIRMED được; một transition, một notification/recipient; canceled/completed không hủy tiếp |

## 5. Notifications — FR-NOTIF-01/02/03/06/07; ALC-42/44/45

| ID | Input/tình huống | Kết quả kỳ vọng |
| --- | --- | --- |
| TC-NOTIF-01 | MA tạo booking cho A1 | A1 một confirmation; MA không mặc định nhận confirmation thay A1 |
| TC-NOTIF-02 | AD hủy booking MA tạo cho A1; owner=creator variant | A1 và MA mỗi người một cancellation; variant một recipient, không trùng |
| TC-NOTIF-03 | A1 list/read notification MA bằng ID | Không truy cập/mark-read được; mark-read của A1 idempotent |
| TC-NOTIF-04 | Admin đổi reminder lead; nonadmin đổi; lead0/1441; booking đã nhắc | Chỉ Admin; invalid400; lead mới chỉ ảnh hưởng chưa nhắc; đã nhắc không gửi lại |
| TC-NOTIF-05 | Email API timeout/retry | Booking/in-app vẫn thành công; không giữ resource lock khi gọi email; không log secrets |

## 6. Catch-up — FR-JOB-01/02, FR-BOOK-06; ALC-44/50 và local job tasks

| ID | Input/tình huống | Kết quả kỳ vọng |
| --- | --- | --- |
| TC-JOB-01 | start10:00 due09:45; created09:30; job09:45,09:48,09:50,09:50:01 | Các lần độc lập <=5min được nhắc một lần; >5min bỏ qua; không resend khi rerun |
| TC-JOB-02 | start10:00 due09:45; created09:50; hoặc job10:00 | Không reminder late-created; không gửi tại/sau start; CONFIRMED->CANCELLED trước job cũng không gửi |
| TC-JOB-03 | Service sleep qua end; nhiều CONFIRMED và CANCELLED; wake lặp | Complete overdue CONFIRMED đúng một lần; CANCELLED giữ nguyên; history changedBy=null |
| TC-JOB-04 | Sleep3ngày; wake hai instance | Catch-up kỳ alert hiện tại một episode, không replay ba ngày notifications; current period phản ánh timezone địa phương |

## 7. Analytics — FR-STAT-01..08; ALC-46/48/49/50/51/52

| ID | Input/tình huống | Kết quả kỳ vọng |
| --- | --- | --- |
| TC-STAT-01 | Operating9h; CONFIRMED4h+COMPLETED4h+CANCELLED1h | Booked480/available540=88.888...%; chuyển CONFIRMED->COMPLETED không giảm tử số |
| TC-STAT-02 | Report range cắt giữa booking; booking tiếp giáp biên from/to | Chỉ overlap minutes; tại to không tính; không cộng toàn booking ngoài kỳ |
| TC-STAT-03 | Không operating windows; đổi schedule cũ; suspended/deleted resource lịch sử | Rate null/N/A nếu denom0; report công bố current-schedule limitation; không clamp>100; vẫn giữ lịch sử |
| TC-STAT-04 | Kỳ alert ngàyD; các booking D-7 đếnD; threshold85,rate85 rồi85.1 | Chỉ [D-7 00:00,D00:00);85 không alert,85.1 có; đúng scope recipients |
| TC-STAT-05 | Vượt->OPEN->ACK->vẫn vượt->hạ<=threshold->vượt lại; concurrent job | ACK vẫn active; không duplicate/spam; resolve rồi episode mới; unavailable resolve với lý do; N/A không kết luận recovery |
| TC-STAT-06 | Trend booking tạo trước kỳ nhưng start trong kỳ; Manager departmentA | Count theo start ngày địa phương/status; report resourceA tính tất cả owner; không rò reportB |

## 8. Map — FR-MAP-01..04; ALC-54/55/56/57

| ID | Input/tình huống | Kết quả kỳ vọng |
| --- | --- | --- |
| TC-MAP-01 | PNG/JPEG thật<=10MiB; fake extension; SVG; oversized | Chỉ ảnh hợp lệ;415/400 cho nội dung sai theo contract,413 quá giới hạn; resource URL không dựa disk Render |
| TC-MAP-02 | Tọa độ trong/ngoài ảnh, width<=0; tablet resize; thay ảnh nhỏ hơn | Valid render đúng tỉ lệ; invalid bị400/409 theo contract; không giữ mapping ngoài ảnh âm thầm |
| TC-MAP-03 | Ngày toàn trống/một phần/đầy/đóng; SUSPENDED/DELETED; ngày hiện tại đã qua slot | Xanh/vàng/đỏ phù hợp; suspended xám, deleted ẩn; không slot quá khứ xanh; kèm label |
| TC-MAP-04 | Chọn range xung đột một phần; người khác đặt sau khi map xanh | Range đỏ không vàng; create revalidate trả409 nếu vừa mất slot, không tạo overlap |

## 9. Account — FR-AUTH-01/02/04/06/07/08; ALC-8/9/26/28

| ID | Input/tình huống | Kết quả kỳ vọng |
| --- | --- | --- |
| TC-AUTH-01 | JWT đúng/sai signature,expired,wrong token type | Chỉ đúng loại access dùng API,refresh dùng refresh; invalid401 |
| TC-AUTH-02 | Issue access rồi disable account, dùng lại access/refresh | Cả hai bị từ chối ngay, không đợi30phút |
| TC-AUTH-03 | Issue refresh versionN; change/reset password; dùng refreshN | Refresh cũ401; login mới nhận versionN+1; password BCrypt>=12; không hứa invalidate mọi access cũ |
| TC-AUTH-04 | Logout client; token copy; Employee sửa role qua profile | Client xóa token; copy chưa revoke có thể còn dùng theo policy; profile không đổi role/department/active |

## 10. Deployment và NFR — ALC-58/60/61/62/63

- TC-DEPLOY-01: staging/demo Vercel->Render HTTPS, JDBC TLS->Supabase, ảnhR2; redeploy Render không mất DB/ảnh; CORS allowlist; secrets không trong code/log; restore thử dữ liệu giả. Sleep/wake đúng catch-up và thông báo giới hạn demo.
- TC-DEPLOY-02: benchmark warmed-up always-on bằng workload<50 in-flight, ghi machine/provider/database version/dataset/run durations, từng latency và p50/p95/p99/max. NFR gốc yêu cầu dưới1s create và500ms list/availability: báo cả vi phạm từng request, không lặng lẽ thay bằng p95. Cold-start đo riêng không gộp. Concurrency testTC-CONC-01 bắt buộc độc lập benchmark.
- Coverage unit service>=70%, integration/PostgreSQL trong pipeline cùng Booking Engine. Lint OpenAPI + validate examples; parse/render Mermaid; regenerate SRS PDF và kiểm tra text/Unicode. CI build/test trên push khi có app source.

## Traceability và Definition of Done

- Mỗi Jira issue trong Backlog.xlsx ghi FR/BR liên quan và nhóm TC phía trên. Local tasks chưa có Jira key không được giả lập issue đã tạo trên server.
- Done: contract+implementation+negative tests+DB assertions+docs đồng bộ; test output thật đính kèm. Không dùng câu “đã test” chỉ vì test plan đã được viết.
- Email/CSV P2 chưa triển khai không chặn core MVP nhưng phải ghi rõ skipped. PDF export ngoài scope.
- Case lỗi auth/scope/resource không được che bởi fixture invalid-time vô tình; dùng clock cố định và baseline hợp lệ trước negative assertion.
