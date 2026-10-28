# Backend trình độ, phép và đơn từ — Xuân Anh

Bàn giao tổng hợp mới nhất: xem [BAN_GIAO_XUAN_ANH.md](BAN_GIAO_XUAN_ANH.md). Chỉ triển khai nhiệm vụ Xuân Anh; tích hợp source nhóm chờ lượt sau.

Phạm vi XA-08 đến XA-13: trình độ, bằng cấp/chứng chỉ, loại nghỉ, hạn mức, nộp/xem đơn nghỉ phép và nghỉ việc, duyệt/từ chối, cập nhật nghỉ việc theo ngày hiệu lực. Tiếp tục phạm vi REST API/backend như G1. Chưa bổ sung màn hình Angular hoặc triển khai JWT của thành viên phụ trách đăng nhập.

## 1. Database

Database HRM cần baseline của nhóm, `001_department_heads.sql`, sau đó `HRM_System/database/migrations/002_personnel_requests.sql` và `003_request_withdrawals.sql`.

Trong SSMS chọn đúng database; thêm `USE [hrm_db]; GO` hoặc `USE [hrm_test]; GO` ở đầu tab theo môi trường thực tế. Migration không tự đổi database. Có thể chạy lại, không DROP/TRUNCATE, không tự cấp chính sách cho loại nghỉ đang dùng.

Migration G2 bổ sung:

- Cột xóa mềm bằng cấp: is_deleted, deleted_at, deleted_by.
- g2_leave_policies: chính sách loại nghỉ có dùng hạn mức hay không. Không suy luận từ is_paid.
- g2_leave_allocations: giờ của từng lịch được duyệt và hạn mức tương ứng; không sửa/xóa lịch sử.
- g2_resignation_executions: đánh dấu thực thi nghỉ việc, chống thực thi hai lần.
- Trigger ngăn thay đổi lịch đã có phép được duyệt, hoặc thêm/đổi lịch chồng đơn phép đã duyệt. Ghi chú lịch vẫn được sửa.

Lượt triển khai đã áp dụng migration trên **hrm_test**, không tự thay đổi **hrm_db**. Các bảng mới cần được đồng bộ lên máy nhóm trước khi chạy G2.

## 2. Chức năng và tác dụng

| Task | Triển khai | Tác dụng |
|---|---|---|
| XA-08 | Giữ CRUD trình độ, mã bất biến, ngừng/kích hoạt; thêm quyền và audit | Dùng chung danh mục, giữ tham chiếu hồ sơ cũ |
| XA-09 | Giữ nhiều bằng, chọn tối đa một bằng chính, xóa mềm; khóa giao dịch, người thao tác, audit | Đổi bằng chính và xóa không phá lịch sử |
| XA-10 | Giữ CRUD loại nghỉ và is_paid; ADMIN đặt consumesEntitlement riêng | Tách hưởng lương khỏi việc dùng hạn mức |
| XA-11 | Tạo đầu đơn + chi tiết phép trong một transaction; xem cá nhân/quản lý | Không có đầu đơn rỗng hoặc xem đơn người khác |
| XA-12 | Nộp/xem đơn nghỉ việc và ngày cuối dự kiến, chặn đơn đang chờ/chờ hiệu lực trùng | Nộp đơn không làm nhân viên nghỉ việc ngay |
| XA-13 | Quyền REQUEST_APPROVE, cấm tự duyệt/từ chối, chỉ xử lý CHO_DUYET một lần; phép và audit cùng transaction | Không duyệt/trừ phép hai lần; lỗi thì rollback |
| Hiệu lực | Sau ngày làm cuối mới đóng phân công và khóa tài khoản; ledger thực thi | Không nghỉ sớm, không mất ngày làm cuối |

Các endpoint cũ của bốn module vẫn giữ đường dẫn, DTO và mã phản hồi thành công. Thay đổi có chủ đích: trước đây các API G2 chưa kiểm tra đăng nhập, nay yêu cầu Principal thực. Không nhận accountId/employeeId của người nộp từ client; xác định từ tài khoản đăng nhập.

## 3. Quyền

- Tài khoản phải hoạt động và nhân viên DANG_LAM, kiểm tra lại khi truy cập.
- ADMIN/HR có EMPLOYEE_MANAGE quản lý danh mục, bằng cấp và hạn mức.
- Người đăng nhập xem danh mục; người sở hữu hoặc quản lý xem bằng cấp, hạn mức, số dư và đơn chi tiết.
- GET /employee-requests/mine chỉ trả đơn của chính tài khoản; GET /employee-requests là hàng đợi của quản lý.
- Duyệt/từ chối cần cả quyền quản lý và REQUEST_APPROVE; không tự duyệt/từ chối đơn của mình kể cả ADMIN.
- Cấu hình chính sách trừ hạn mức và chạy thực thi nghỉ việc thủ công chỉ ADMIN.
- Không tự áp dụng hr_department_scopes vào G2: tài liệu nhóm hiện mô tả scope cho lịch; cần chốt chính sách liên module trước khi mở rộng.

## 4. Tính phép

Luồng: ADMIN tạo loại nghỉ → cấu hình chính sách → quản lý cấp hạn mức nếu cần → nhân viên nộp → quản lý duyệt/từ chối → đọc số dư.

- is_paid dùng biểu diễn chế độ hưởng lương, không tự cấp/trừ phép. G2 chưa tính bảng lương.
- Mỗi loại mới phải có chính sách rõ qua PUT /employee-requests/leave-policies/{id}, body `{"consumesEntitlement":true}` hoặc false.
- Loại đã có đơn không đổi chính sách; loại cũ đã có đơn mà chưa được đối soát dùng loại mới khi thử G2. Không tự gán chính sách cho lịch sử cũ.
- Khoảng nghỉ là [leaveStart,leaveEnd), giờ địa phương Việt Nam như work_schedules. Thời điểm audit/submitted/processed là UTC, response có Z.
- Chỉ tính phần giao với lịch làm việc. Nghỉ trọn ca: thời lượng ca trừ planned_break_minutes. Không tính ngày không có lịch. Không mặc định 8 giờ/ngày hoặc số ngày phép theo năm.
- Nếu nghỉ một phần ca có break > 0 nhưng không biết vị trí nghỉ giữa ca, từ chối và yêu cầu lịch được chia chính xác hoặc chọn trọn ca; không tự đoán giờ ăn trưa.
- Mỗi ca thuộc kỳ hạn mức theo work_date, [periodStart,periodEnd). Ca qua đêm thuộc ngày bắt đầu lịch.
- Đơn phải ở tương lai khi nộp và khi duyệt; kiểm tra lại loại nghỉ, lịch, trùng đơn, số dư tại thời điểm duyệt.
- Pending không giữ chỗ số giờ. Hai đơn khác giờ có thể cùng chờ; đơn duyệt sau có thể bị từ chối vì hết phép.
- `entitledHours` là số được cấp, không giảm khi duyệt. `usedHours` là SUM phân bổ của đơn DA_DUYET; remainingHours = entitledHours - usedHours.
- Sửa hạn mức không được thấp hơn giờ đã duyệt hoặc làm kỳ mới không bao phủ các ngày đã phân bổ.
- Sau duyệt không sửa đơn/chi tiết/phân bổ và lịch ảnh hưởng số giờ. Hủy/điều chỉnh đơn đã duyệt cần quy trình bù trừ riêng, chưa thuộc task tạo/xem/duyệt hiện tại.
- Từ chối bắt buộc lý do, không tính vào số đã dùng. Có thể nộp đơn mới sau từ chối.

## 5. Nghỉ việc

Luồng: CHO_DUYET → DA_DUYET hoặc TU_CHOI. Khi DA_DUYET, ngày cuối được chấp thuận nằm ở chi tiết, nhân viên vẫn DANG_LAM trong ngày làm cuối.

Sau 00:00 ngày kế tiếp theo Asia/Ho_Chi_Minh, runner áp dụng: đóng phân công tại lastWorkingDate + 1, đặt termination_date đúng ngày cuối, DA_NGHI, khóa các tài khoản, lưu execution/audit cùng transaction.

Kiểm tra cả lúc duyệt và lúc thực thi: chưa có trưởng phòng vượt ngày cuối, không có phân công mới sau ngày cuối, không còn lịch/hợp đồng đang hiệu lực/phép đã duyệt vượt ngày cuối. G2 không tự sửa hợp đồng, chấm công hoặc bảng lương của thành viên khác. Nếu còn phụ thuộc, trả BLOCKED kèm lý do; xử lý ở module phụ trách rồi chạy lại. Mỗi đơn thực thi trong transaction riêng; một đơn lỗi không rollback các đơn khác.

Scheduler được cung cấp nhưng mặc định tắt để không tự cập nhật nhân sự khi vừa nhận source. Trên môi trường đã đồng bộ database và được nhóm bật vận hành, thêm vào cấu hình local:

```properties
personnel.resignations.scheduler-enabled=true
personnel.resignations.interval-ms=60000
```

Hoặc ADMIN gọi POST /employee-requests/resignations/execute-due. Runner đọc từng trang 100 đơn theo ID, tự đi tiếp qua các đơn bị BLOCKED để không làm các đơn sau bị bỏ đói. Chạy lại sau khi đã thực thi không thực thi cùng đơn lần nữa. Runner không dùng quyền của tài khoản test; scheduler ghi actor null với action RESIGNATION_EXECUTE, API thủ công ghi ADMIN thực hiện.

API nghỉ việc trực tiếp G1 vẫn tồn tại. Đó là đường xử lý ngay cho quản lý; tránh sử dụng cho nhân viên đã có đơn chờ hiệu lực vì có thể làm đơn đó bị BLOCKED khi runner kiểm tra trạng thái.

## 6. API mới

| Method | Đường dẫn | Ý nghĩa |
|---|---|---|
| POST | /employee-requests/leave | `{leaveTypeId,leaveStart,leaveEnd,reason}` |
| POST | /employee-requests/resignation | `{requestedLastWorkingDate,reason}` |
| GET | /employee-requests/mine | status,type,page,size tùy chọn |
| GET | /employee-requests | Quản lý xem/lọc hàng đợi |
| GET | /employee-requests/{id} | Chi tiết, trạng thái, phân bổ đã duyệt |
| POST | /employee-requests/{id}/approve | `{approvedLastWorkingDate?,note?}`, đơn phép gửi `{}` được |
| POST | /employee-requests/{id}/reject | `{reason}` bắt buộc |
| POST | /employee-requests/{id}/withdraw | Chủ đơn rút đơn chờ, `{reason}` bắt buộc; giữ lịch sử |
| GET | /employee-requests/balances/{employeeId} | Số giờ được cấp/đã dùng/còn lại |
| PUT/GET | /employee-requests/leave-policies/{leaveTypeId} | Thiết lập/đọc chính sách |
| POST | /employee-requests/resignations/execute-due | ADMIN thực thi các đơn đã tới hạn |
| GET | /personnel/audit?targetTable=employee_requests&recordId=... | Audit phân trang, chỉ quản lý; giữ alias /g2/audit |
| GET | /audit-logs | ADMIN tra cứu audit dùng chung |

Các route cũ qualification-levels, employee-qualifications, employees/{id}/qualifications, leave-types, leave-entitlements vẫn giữ.

## 7. Chạy kiểm tra

Kết quả kiểm tra ngày 28/10/2026: toàn bộ lượt kiểm thử backend nhân sự có **123 test Java đạt, 0 thất bại/bỏ qua**. Collection trình độ/phép/đơn từ chạy **81 request, 91 assertion, 0 lỗi**. Hồi quy collection G1 có **149 request, 176 assertion đạt, 0 lỗi**. Đóng gói jar thành công; không chứa server test, datasource local, JWT hoặc source payroll nhập thử. Xem kết quả mới nhất khi tự chạy, không dùng report cũ làm bằng chứng cho code vừa thay đổi.

Trong PowerShell:

```powershell
cd C:\NguyenXuanAnh\Git-Nhom-HRM\HRM_System
powershell -NoProfile -ExecutionPolicy Bypass -File .\run-personnel.ps1 -Mode test
```

Script yêu cầu hrm_test, không tự chạy migration. Fixture integration test rollback, không xóa dữ liệu. Các test dùng Clock điều khiển thời gian để kiểm tra sau ngày làm cuối mà không đổi giờ máy.

Postman:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\run-personnel.ps1 -Mode postman
```

Chờ PERSONNEL_POSTMAN_READY. Server TEST-ONLY nghe 127.0.0.1:2007, không nằm trong jar. Import test-tools/personnel.postman_collection.json và target/personnel-postman-runtime.postman_environment.json. Chọn HRM Personnel — local test session; bật runMutations=true, chạy cả collection 1 iteration theo thứ tự. Mỗi lần server khởi động phải import environment mới.

Harness tạo hai nhân viên/tài khoản test PERSONNEL-POSTMAN-* có ADMIN/EMPLOYEE thật và 60 ca thử nghiệm cho nhân viên. Dừng bình thường khóa đúng hai tài khoản đó. Không mở tài khoản cá nhân/seed cũ. Mỗi lần khởi động tạo fixture riêng để bảo toàn lịch sử; dữ liệu Postman được commit trong hrm_test và giữ lại.

Chạy toàn bộ chuỗi **một lần trên mỗi phiên server**. Để chạy lại, dừng rồi khởi động server mới và import lại environment: phiên mới có nhân viên test riêng. Lần trước đã có phép được duyệt và đơn nghỉ việc chờ hiệu lực, nên không dùng lại nhân viên đó để chạy chuỗi từ đầu. Postman kiểm tra duyệt nghỉ việc tương lai chưa thực thi ngay; trường hợp tới ngày thực thi được kiểm tra trong Java bằng Clock.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\run-personnel.ps1 -Mode stop
```

Không đưa target/, environment runtime, token, log/report raw, application-local.properties hoặc tài liệu cá nhân lên Git. Bàn giao source nhân sự/đơn từ, migration 002 và 003, script runner và collection/generator nếu nhóm cần tái kiểm tra. Không cần đưa file hướng dẫn này lên nếu chỉ muốn source.
