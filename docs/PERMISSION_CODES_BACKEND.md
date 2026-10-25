# Mã quyền đang dùng trong backend

Tài liệu ghi lại việc đồng bộ các API đã có với bộ **18 permission_code chữ hoa** trong [create_database_ONLY_EMP.sql](../create_database_ONLY_EMP.sql) và [seed_an_phuc_ONLY_EMP.sql](../seed_an_phuc_ONLY_EMP.sql). Bộ mã trong SQL là căn cứ cho lần sửa này; việc có mã trong script không xác nhận script đã được chạy trên database đang kết nối.

Backend dùng nguyên mã từ `permissions.permission_code` qua `role_permissions`. Lần sửa này không thêm, đổi hoặc xóa mã trong database, không chạy migration và không tạo alias giữa mã dấu chấm với mã chữ hoa. Vai trò `ADMIN` cũng phải có đúng permission mà API yêu cầu.

## 1. API và điều kiện hiện áp dụng

Trong bảng dưới, “ADMIN/HR + quyền” nghĩa là phải đồng thời có role `ADMIN` hoặc `HR` và có permission được ghi. EMPLOYEE bị từ chối API quản trị ngay cả khi vô tình được gán permission quản lý.

| API | Điều kiện truy cập |
| --- | --- |
| `GET /departments`, `GET /departments/{id}`, `POST /departments`, `PUT /departments/{id}`, `DELETE /departments/{id}` | ADMIN/HR + `DEPARTMENT_MANAGE` |
| `GET /positions`, `GET /positions/{id}`, `POST /positions`, `PUT /positions/{id}`, `DELETE /positions/{id}` | ADMIN/HR + `POSITION_MANAGE` |
| `GET /employees`, `POST /employees`, `PUT /employees/{id}`, `DELETE /employees/{id}` | ADMIN/HR + `EMPLOYEE_MANAGE` |
| `GET /employees/{id}` | Nhánh quản trị: ADMIN/HR + `EMPLOYEE_MANAGE`; **hoặc** nhánh cá nhân: `SELF_READ` và `{id}` đúng bằng `employeeId` của principal đã xác thực |
| `GET /api/reports/employees`, `GET /api/reports/contracts`, `GET /api/reports/work` | ADMIN/HR + `REPORT_VIEW` |
| `GET /api/reports/payroll` | ADMIN/HR + đồng thời `REPORT_VIEW` và `PAYROLL_VIEW`; chỉ tổng hợp batch đã chốt mới nhất của từng kỳ |
| `GET /api/reports/employees/export`, `GET /api/reports/contracts/export`, `GET /api/reports/work/export` | ADMIN/HR + `REPORT_VIEW`; xuất cùng dữ liệu và bộ lọc với báo cáo JSON tương ứng |
| `GET /api/reports/payroll/export` | ADMIN/HR + đồng thời `REPORT_VIEW` và `PAYROLL_VIEW`; điều kiện chọn batch giống báo cáo lương JSON |
| `GET /api/me/salary` | `PAYSLIP_SELF_READ`; chỉ truy vấn dữ liệu lương cá nhân từ batch đã chốt và công bố, theo `employeeId` từ principal |
| `GET /api/me/payslips` | `PAYSLIP_SELF_READ`; chỉ truy vấn phiếu lương cá nhân từ batch đã chốt và công bố, theo `employeeId` từ principal |
| `POST /api/admin/accounts/{accountId}/reset-password` | ADMIN + `ACCOUNT_MANAGE`; giữ kiểm tra không reset chính mình qua API quản trị |

Nhánh cá nhân của `GET /employees/{id}` áp dụng cho cả ADMIN, HR và EMPLOYEE. Một HR chỉ có `SELF_READ` có thể xem hồ sơ mình, nhưng không xem hồ sơ người khác hoặc danh sách nhân viên. Quyền `EMPLOYEE_MANAGE` không được dùng để mở danh sách cho EMPLOYEE.

Hai API `/api/me/salary` và `/api/me/payslips` đều cung cấp dữ liệu lương cá nhân nên cùng yêu cầu `PAYSLIP_SELF_READ`. Không dùng `SELF_READ` để đọc lương qua đường dẫn khác. `PAYROLL_VIEW` và `PAYROLL_MANAGE` cũng không tự cho phép hai API cá nhân này; chúng không tạo quan hệ kế thừa hoặc quyền tự động.

API cá nhân không nhận một employeeId do client chỉ định để đổi chủ dữ liệu. ADMIN/HR gọi API cá nhân cũng chỉ đọc chính họ. Các API quản trị và API cá nhân là hai phạm vi khác nhau.

Các API xuất Excel kiểm tra cùng điều kiện quyền tại controller và service. Mỗi lần tạo file thành công ghi `audit_logs` với action `REPORT_EXPORT_GENERATED`, người thực hiện và bộ lọc đã áp dụng; không lưu nội dung file hoặc số tiền vào audit. Ghi audit thất bại thì không trả file. Các endpoint xuất không mở thêm quyền truy cập dữ liệu chi tiết ngoài DTO báo cáo. Xem [hướng dẫn xuất Excel](XUAT_BAO_CAO_EXCEL.md).

Endpoint, DTO và xử lý nghiệp vụ không thay đổi theo việc đổi tên authority. Quyền xóa không cho phép bỏ qua quy tắc lưu lịch sử, chuyển trạng thái nhân viên hoặc ngừng sử dụng danh mục.

## 2. Đăng nhập và vòng đời phiên

Các API đăng nhập, đăng xuất và đổi mật khẩu:

| API | Cách sử dụng |
| --- | --- |
| `POST /api/auth/login` | Không yêu cầu token; gửi username/password để đăng nhập |
| `POST /api/auth/logout` | Yêu cầu danh tính đã xác thực; đăng xuất tài khoản hiện tại |
| `POST /api/auth/change-password` | Yêu cầu danh tính đã xác thực và đúng mật khẩu hiện tại; chỉ đổi cho tài khoản hiện tại |

Không bổ sung permission cho ba thao tác này. Việc đồng bộ permission không thay đổi cách sinh JWT, kiểm tra phiên hoặc chính sách token_version.

**Điều kiện tài khoản đang được sử dụng:** `accounts.is_active = 1` và nhân viên liên kết phải có `employment_status = DANG_LAM`. Login và mỗi lần xác thực Bearer Token đều tra điều kiện này từ DB, áp dụng cho ADMIN/HR/EMPLOYEE. Nhân viên đã nghỉ từ trước bị từ chối ngay cả khi account còn active và JWT chưa hết hạn. Các thao tác đổi/reset mật khẩu cũng yêu cầu tài khoản gắn với nhân viên còn làm việc.

`DELETE /employees/{id}` chuyển nhân viên sang `DA_NGHI`, ghi ngày nghỉ nếu chưa nghỉ, khóa account liên kết và tăng `token_version` trong cùng transaction. Nếu chưa có account thì vẫn cho nghỉ việc. Gọi lại với người đã nghỉ giữ nguyên ngày nghỉ cũ và tiếp tục khóa/thu hồi account nếu có; token version có thể tăng thêm. Hồ sơ và dữ liệu lịch sử vẫn được giữ lại.

API được bảo vệ trả **401 kèm JSON lỗi** khi thiếu/sai/hết hạn JWT hoặc tài khoản không còn hợp lệ; tài khoản đã xác thực nhưng thiếu quyền thao tác trả **403**. Login của người đã nghỉ trả 401 với thông báo đăng nhập chung, không tiết lộ trạng thái nhân viên.

Đối chiếu sau khi khởi động lại backend: lưu token của nhân viên thử còn làm việc, dùng tài khoản ADMIN/HR có `EMPLOYEE_MANAGE` cho người đó nghỉ, rồi kiểm tra `accounts.is_active = 0`, `token_version` tăng, token cũ bị 401 và đăng nhập lại bị 401. Dùng hồ sơ/tài khoản thử vì thao tác này thay đổi dữ liệu. Đây là hướng dẫn kiểm tra, không phải kết quả kiểm thử đã chạy.

## 3. Đối chiếu đủ 18 mã đã có trong SQL

Tám mã đã được API hiện tại sử dụng:

| Mã | Nơi sử dụng |
| --- | --- |
| `ACCOUNT_MANAGE` | Reset mật khẩu tài khoản khác của ADMIN |
| `EMPLOYEE_MANAGE` | Quản trị nhân viên |
| `SELF_READ` | Đọc chi tiết hồ sơ của chính mình |
| `PAYSLIP_SELF_READ` | Hai API lương/phiếu lương cá nhân |
| `DEPARTMENT_MANAGE` | Quản trị phòng ban |
| `POSITION_MANAGE` | Quản trị chức vụ |
| `REPORT_VIEW` | Báo cáo nhân sự, hợp đồng, công và quỹ lương; báo cáo quỹ lương cần thêm `PAYROLL_VIEW` |
| `PAYROLL_VIEW` | Báo cáo quỹ lương quản trị, yêu cầu thêm `REPORT_VIEW` và role ADMIN/HR |

Mười mã còn lại được giữ nguyên trong bộ SQL, chờ triển khai API tương ứng và xác định cụ thể phạm vi thao tác:

| Mã | Phần cần triển khai hoặc xác định |
| --- | --- |
| `REQUEST_APPROVE` | Duyệt/từ chối đơn nghỉ phép, nghỉ việc; chốt phạm vi xem và quy tắc người xử lý |
| `PAYROLL_MANAGE` | API quản trị kỳ lương, tính/chốt/điều chỉnh; không suy ra quyền sửa trực tiếp lương đã chốt |
| `CONTRACT_MANAGE` | Quản trị hợp đồng và lịch sử |
| `WORK_SCHEDULE_MANAGE` | Quản trị lịch, cùng phạm vi nhân viên HR được phân lịch |
| `ATTENDANCE_MANAGE` | Ghi nhận, quản lý hoặc xác nhận công; cần thống nhất phạm vi vì nhãn SQL đang là “Xác nhận chấm công” |
| `LEAVE_REQUEST_CREATE` | Tạo đơn nghỉ phép cá nhân |
| `OVERTIME_REQUEST_CREATE` | Đăng ký tăng ca cá nhân |
| `OVERTIME_APPROVE` | Duyệt/từ chối tăng ca; xác định quyền xác nhận giờ thực tế |
| `ALLOWANCE_MANAGE` | Quản trị phụ cấp và thời gian áp dụng |
| `DECISION_MANAGE` | Quản trị quyết định khen thưởng/kỷ luật |

Không tự coi `PAYROLL_MANAGE` bao gồm `PAYROLL_VIEW`, `REQUEST_APPROVE` bao gồm `OVERTIME_APPROVE`, hoặc `SELF_READ` bao gồm `PAYSLIP_SELF_READ`. Khi các API mới được xây dựng, phải khai báo đúng điều kiện quyền và phạm vi thay vì dựa vào nhãn hiển thị.

## 4. Giới hạn còn cần xử lý

- **Điều kiện công bố lương đã đồng bộ ở code:** [PayrollRepository.java](../HRM_System/src/main/java/com/hrm/repository/PayrollRepository.java) lọc `DA_CHOT`, đủ metadata chốt/công bố và `published_at >= locked_at`. Hai API cá nhân giữ danh sách lịch sử các bản đã công bố của chính người gọi, sắp kỳ/phiên bản mới trước; không cộng gộp các phiên bản. Nháp và bản chốt chưa công bố không xuất hiện. Chưa có kiểm thử SQL Server chứng minh toàn bộ luồng phát hành/công bố; danh sách rỗng vẫn hợp lệ khi chính người gọi chưa có dữ liệu đủ điều kiện.
- **Phạm vi HR khi phân lịch chưa được triển khai:** đổi permission không tạo bộ lọc theo phòng ban, nhân viên hay chính sách phân lịch. `WORK_SCHEDULE_MANAGE` đang chờ API và quy tắc scope cụ thể; không được dùng lần sửa này để xác nhận HR đã được giới hạn đúng phạm vi lịch làm việc.
- **Đây không phải biên bản kiểm thử:** các bước dưới là hướng dẫn kiểm tra sau khi khởi động lại backend. Kết quả kiểm thử tự động và Postman cần ghi nhận riêng; tài liệu không khẳng định toàn bộ 15 chức năng hoặc ma trận quyền đã được nghiệm thu.

## 5. Kiểm tra lại bằng Postman

1. Khởi động lại backend để nạp code mới. Gửi **POST** đến `http://localhost:2005/api/auth/login`, chọn **Authorization → No Auth**, **Body → raw → JSON**, dùng username/password thực tế của tài khoản kiểm thử. Khi thành công, lấy riêng giá trị `accessToken`, không lấy cả JSON.
2. Với các request sau, chọn **Authorization → Bearer Token** và dán token vừa nhận. Không thêm chữ `Bearer` vào ô Token.
3. Kiểm tra bằng tài khoản có role và quyền tương ứng:

   | Request đầy đủ | Điều kiện và kết quả kỳ vọng |
   | --- | --- |
   | **GET** `http://localhost:2005/departments` | ADMIN/HR có `DEPARTMENT_MANAGE`: 200 nếu xử lý DB thành công; thiếu quyền hoặc EMPLOYEE: 403 |
   | **GET** `http://localhost:2005/positions` | ADMIN/HR có `POSITION_MANAGE`: 200 nếu xử lý DB thành công; thiếu quyền hoặc EMPLOYEE: 403 |
   | **GET** `http://localhost:2005/employees` | ADMIN/HR có `EMPLOYEE_MANAGE`: 200 nếu xử lý DB thành công; EMPLOYEE: 403 |
   | **GET** `http://localhost:2005/employees/{id}` | Thay `{id}` bằng ID thật: người có `SELF_READ` xem đúng ID của mình được phép; xem ID khác khi không có nhánh quản trị bị 403 |
   | **GET** `http://localhost:2005/api/reports/employees` | ADMIN/HR có `REPORT_VIEW`: 200 nếu xử lý DB thành công; EMPLOYEE: 403 |
   | **GET** `http://localhost:2005/api/reports/contracts?fromDate=2026-01-01&toDate=2026-12-31` | ADMIN/HR có `REPORT_VIEW`: được qua kiểm tra quyền; kiểm tra tiếp dữ liệu/bộ lọc |
   | **GET** `http://localhost:2005/api/reports/work?fromDate=2026-01-01&toDate=2026-12-31` | ADMIN/HR có `REPORT_VIEW`: được qua kiểm tra quyền; kiểm tra tiếp dữ liệu/bộ lọc |
   | **GET** `http://localhost:2005/api/reports/payroll?fromDate=2026-08-01&toDate=2026-08-31` | ADMIN/HR có cả `REPORT_VIEW` và `PAYROLL_VIEW`: 200 khi DB hợp lệ; thiếu một quyền hoặc là EMPLOYEE: 403. Chỉ tính batch `DA_CHOT` mới nhất mỗi kỳ |
   | **GET** `http://localhost:2005/api/me/salary` | Có `PAYSLIP_SELF_READ`: được qua kiểm tra quyền; chỉ có `SELF_READ`, `PAYROLL_VIEW` hoặc `PAYROLL_MANAGE`: 403 |
   | **GET** `http://localhost:2005/api/me/payslips` | Cùng yêu cầu `PAYSLIP_SELF_READ`; chỉ có phiếu của người gọi thuộc batch đã chốt và công bố. Cần có dữ liệu thử đủ điều kiện để đối chiếu kết quả |

4. Đăng xuất cuối buổi bằng **POST** `http://localhost:2005/api/auth/logout`, kèm token hiện tại. Đổi mật khẩu khi thực sự cần bằng **POST** `http://localhost:2005/api/auth/change-password`; thao tác này làm thay đổi tài khoản, nên không dùng như API đọc để kiểm tra quyền phòng ban.

Các ca Postman phải dùng đúng quyền đã gán trong database đang chạy. Chỉ đăng nhập thấy role ADMIN trong token chưa đủ để kết luận API nào cũng được mở. Không đưa token hoặc mật khẩu thật vào tài liệu kết quả.
