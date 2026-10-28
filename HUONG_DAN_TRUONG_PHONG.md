# Hướng dẫn phần trưởng phòng cho Xuân Anh

## Phần đã được triển khai

Đã xác minh ngày 23/10/2026: Maven chạy 21 test, không lỗi; trong đó có 18 integration test trưởng phòng và 1 test khóa đồng thời mới. Angular production build thành công, kiểm tra TypeScript/template strict. Chưa kiểm thử đăng nhập và thao tác qua trình duyệt vì repo chưa có JWT; test HTTP dùng Principal được cung cấp bởi môi trường kiểm thử, không có cơ chế giả lập đăng nhập trong sản phẩm.

Trưởng phòng là một phân công có chức vụ `TRUONG_PHONG`. Không thêm bảng mới hoặc cột department_head_id. Mỗi lần thay người đóng khoảng cũ và tạo khoảng mới; mọi khoảng ngày dùng `[effective_from,effective_to)`.

Ví dụ thay ngày 23/10: người cũ giữ chức vụ đến trước ngày 23/10, người mới đảm nhiệm từ 23/10. Người cũ chuyển sang chức vụ bạn chọn trong cùng phòng, không bị tự chuyển thành nhân viên với một chức vụ giả định.

Quy tắc bản này: cho phép ngày hiệu lực trong quá khứ hoặc hôm nay, sau ngày bắt đầu phân công mở của từng người liên quan; chưa đặt lịch tương lai. Không tạo phân công dài 0 ngày. Nếu vừa được phân công hôm nay, bổ nhiệm tiếp từ ngày sau. Phòng được phép chưa có trưởng phòng. Kết thúc nhiệm kỳ khác với nghỉ việc.

## Đọc và học cách sửa theo thứ tự

| Thứ tự | File | Nội dung cần hiểu |
| --- | --- | --- |
| 1 | [Migration](C:/NguyenXuanAnh/Git-Nhom-HRM/HRM_System/database/migrations/001_department_heads.sql) | Seed chức vụ, kiểm tra dữ liệu cũ, trigger chống phân công/trưởng phòng chồng ngày và bảo vệ trạng thái |
| 2 | [DepartmentHeadDTO](C:/NguyenXuanAnh/Git-Nhom-HRM/HRM_System/src/main/java/com/hrm/dto/DepartmentHeadDTO.java), [request bổ nhiệm](C:/NguyenXuanAnh/Git-Nhom-HRM/HRM_System/src/main/java/com/hrm/dto/DepartmentHeadRequest.java), [request kết thúc](C:/NguyenXuanAnh/Git-Nhom-HRM/HRM_System/src/main/java/com/hrm/dto/DepartmentHeadEndRequest.java) | Tách dữ liệu đầu vào/đầu ra, không nhận actor/role từ client |
| 3 | [AssignmentGuardService](C:/NguyenXuanAnh/Git-Nhom-HRM/HRM_System/src/main/java/com/hrm/service/AssignmentGuardService.java) | Khóa transaction chung cho các đường ghi lịch sử phân công; hai yêu cầu không cùng vượt qua kiểm tra |
| 4 | [DepartmentHeadService](C:/NguyenXuanAnh/Git-Nhom-HRM/HRM_System/src/main/java/com/hrm/service/DepartmentHeadService.java) | Kiểm tra → đóng/mở phân công → ghi audit trong cùng transaction; xem hiện tại/theo ngày, lịch sử và ứng viên có phân trang |
| 5 | [DepartmentHeadAccessService](C:/NguyenXuanAnh/Git-Nhom-HRM/HRM_System/src/main/java/com/hrm/service/DepartmentHeadAccessService.java) | Principal do server xác thực → accounts.username → tài khoản còn hoạt động, nhân viên đang làm, ADMIN/HR có EMPLOYEE_MANAGE |
| 6 | [DepartmentHeadController](C:/NguyenXuanAnh/Git-Nhom-HRM/HRM_System/src/main/java/com/hrm/controller/DepartmentHeadController.java) | Nhận HTTP/DTO, validation và principal rồi gọi service |
| 7 | [DepartmentService](C:/NguyenXuanAnh/Git-Nhom-HRM/HRM_System/src/main/java/com/hrm/service/DepartmentService.java), [DepartmentDTO](C:/NguyenXuanAnh/Git-Nhom-HRM/HRM_System/src/main/java/com/hrm/dto/DepartmentDTO.java) | Gắn trưởng phòng vào danh sách/chi tiết bằng truy vấn theo cả trang, tránh truy vấn riêng từng phòng |
| 8 | [EmployeeAssignmentService](C:/NguyenXuanAnh/Git-Nhom-HRM/HRM_System/src/main/java/com/hrm/service/EmployeeAssignmentService.java), [EmployeeService](C:/NguyenXuanAnh/Git-Nhom-HRM/HRM_System/src/main/java/com/hrm/service/EmployeeService.java), [PositionService](C:/NguyenXuanAnh/Git-Nhom-HRM/HRM_System/src/main/java/com/hrm/service/PositionService.java) | Đường ghi cũ cùng dùng khóa; không gán/chuyển trưởng phòng để đi vòng workflow; sửa lỗi ngày và chặn ngừng chức vụ hệ thống |
| 9 | [Trang Phòng ban](C:/NguyenXuanAnh/Git-Nhom-HRM/frontend/src/app/pages/departments/departments.ts), [template](C:/NguyenXuanAnh/Git-Nhom-HRM/frontend/src/app/pages/departments/departments.html), [API service](C:/NguyenXuanAnh/Git-Nhom-HRM/frontend/src/app/services/department.service.ts) | Danh sách, ứng viên, form bổ nhiệm/thay/kết thúc và lịch sử; thông báo lỗi từ backend |

Bạn không cần chép lại code thủ công: các file trên đã được sửa trong workspace. Đọc lần lượt để hiểu luồng, rồi thử theo hướng dẫn dưới đây.

## Bước 1 Cập nhật database

Trên máy hiện tại, migration đã được áp dụng thành công vào `hrm_test` ngày 23/10/2026: có chức vụ TRUONG_PHONG hoạt động và đủ 4 trigger mới. Không có nhân viên kiểm thử nào còn lại sau test. Các bước bên dưới dành cho máy thành viên khác hoặc khi cần kiểm tra lại; không cần tạo lại database.

Mở SSMS, chọn đúng database ứng dụng đang cấu hình trong application-local.properties. Khi kiểm tra, máy này dùng `hrm_test`; không nhầm với HRMCenter hoặc hrm_db trong tài liệu cũ.

Mở và chạy toàn bộ `HRM_System/database/migrations/001_department_heads.sql`. Script không tự đổi database, có thể chạy lại, không xóa dữ liệu hoặc tự sửa lịch sử cũ. Nếu báo dữ liệu chồng ngày hoặc chức vụ hệ thống đang vô hiệu hóa, đối soát bản ghi trước; không bỏ CHECK/trigger để chạy cho qua.

Kiểm tra sau migration:

```sql
SELECT position_id, position_code, position_name, is_active
FROM dbo.positions WHERE position_code='TRUONG_PHONG';

SELECT name FROM sys.triggers WHERE name IN
('tr_assignments_timeline_guard','tr_departments_head_active',
 'tr_employees_head_termination','tr_positions_head_code');
```

Script không chứa migration ba cột xóa mềm trình độ vì đó là hạng mục khác trong báo cáo rà soát. Thành viên dùng DB mới vẫn cần schema nền và migration trình độ tương ứng.

## Bước 2 Chạy backend và kiểm thử

Tại thư mục chứa pom.xml:

```powershell
cd C:\NguyenXuanAnh\Git-Nhom-HRM\HRM_System
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

Đặt JAVA_HOME trỏ JDK của máy, theo dự án là JDK 21. Máy rà soát sử dụng JDK 23 để biên dịch release 21; không suy ra đã kiểm thử runtime JDK 21. Nếu Wrapper trên máy không chạy, có thể dùng Maven đã cài với `mvn test`; không sửa pom chỉ để đổi đường dẫn Java cá nhân.

Integration test yêu cầu SQL Server có schema nền và tên database chứa `test`. Dữ liệu mẫu, audit và migration trong mỗi test được rollback; không chỉ định database thật cho test. Khóa đồng thời được kiểm tra bằng hai giao dịch SQL Server thực tế.

Test nằm ở [DepartmentHeadIntegrationTest](C:/NguyenXuanAnh/Git-Nhom-HRM/HRM_System/src/test/java/com/hrm/hrm/service/DepartmentHeadIntegrationTest.java) và [AssignmentLockConcurrencyTest](C:/NguyenXuanAnh/Git-Nhom-HRM/HRM_System/src/test/java/com/hrm/hrm/service/AssignmentLockConcurrencyTest.java). Bao gồm ranh giới ngày, thay người, chức vụ người cũ, audit, người đã nghỉ, phòng ngừng hoạt động, phạm vi phòng, 401/403, validation HTTP, ngăn đi vòng API và trigger chống trùng.

## Bước 3 Nối xác thực của Việt

Repo hiện chưa có login/JWT. Các API phòng ban và trưởng phòng yêu cầu Principal nên khi gọi chưa đăng nhập sẽ trả 401. Đây là điều kiện tích hợp còn thiếu, không phải lỗi proxy và không giải quyết bằng mở quyền công khai hoặc gửi X-Role/X-Account-Id.

Việt cần để cơ chế xác thực thật đưa username đã xác minh vào `request.getUserPrincipal().getName()` (hoặc chuyển access service sang interface CurrentUser thống nhất của nhóm). Tài khoản phải đang hoạt động, nhân viên đang làm việc, role ADMIN/HR có permission EMPLOYEE_MANAGE. JWT filter không được chỉ đọc username do client tự khai mà chưa xác minh token. Nếu principal của nhóm dùng accountId thay username, sửa duy nhất điểm tra cứu này theo hợp đồng đã chốt.

Không tạo login giả, tài khoản mặc định hay cơ chế tự chọn vai trò trên giao diện. Các endpoint nhân sự cũ ngoài phòng ban vẫn cần Việt hoàn thiện bảo mật chung. Không tự cấp quyền duyệt đơn cho chức vụ trưởng phòng.

## Bước 4 Thử API khi đã có phiên xác thực

Backend chạy cổng 2005, các đường dẫn sau chưa có tiền tố `/api` ở phía Spring:

| Method | URL | Kết quả |
| --- | --- | --- |
| GET | /departments?page=0&size=10 | Danh sách có headEmployeeId/headEmployeeCode/headFullName/headEffectiveFrom; vẫn dùng Spring Page như API cũ |
| GET | /departments/ID/head | Trưởng phòng hiện tại; 204 nếu trống |
| GET | /departments/ID/head?asOf=2026-10-20 | Trưởng phòng tại ngày được chọn, giữ đúng lịch sử |
| GET | /departments/ID/head/history?page=0&size=10 | Lịch sử, JSON items/page/size/totalElements/totalPages |
| GET | /departments/ID/head/candidates?page=0&size=10 | Nhân viên đang làm, thuộc phòng, phân công mở đã bắt đầu, chưa là trưởng phòng |
| PUT | /departments/ID/head | Bổ nhiệm lần đầu hoặc thay trưởng phòng |
| POST | /departments/ID/head/end | Kết thúc nhiệm kỳ, trả 204 |

Thay ID bằng giá trị thật từ API của máy bạn. Không gửi nguyên các ID minh họa dưới đây nếu chúng không thuộc đúng phòng.

Bổ nhiệm lần đầu:

```json
{
  "employeeId": 123,
  "effectiveFrom": "2026-10-23",
  "reason": "Bổ nhiệm trưởng phòng theo quyết định"
}
```

Thay trưởng phòng:

```json
{
  "employeeId": 456,
  "effectiveFrom": "2026-10-23",
  "previousHeadPositionId": 7,
  "reason": "Thay trưởng phòng; người cũ tiếp tục làm chuyên viên"
}
```

`previousHeadPositionId` là chức vụ còn hoạt động, khác TRUONG_PHONG, áp dụng cho người cũ tại cùng phòng. Nếu muốn người cũ điều chuyển, kết thúc/thay nhiệm kỳ trước, sau đó thực hiện điều chuyển với ngày hợp lệ. Không ghi hai phân công có độ dài 0 trong cùng ngày.

Kết thúc nhiệm kỳ để phòng tạm trống:

```json
{
  "effectiveFrom": "2026-10-23",
  "nextPositionId": 7,
  "reason": "Kết thúc nhiệm kỳ trưởng phòng"
}
```

Ngày này là ngày đầu không còn giữ chức trưởng phòng, không phải ngày cuối bao gồm. Muốn xử lý nghỉ việc cho người đang là trưởng phòng, kết thúc nhiệm kỳ trước rồi dùng luồng nghỉ việc. Không xóa hồ sơ nhân viên.

Đối soát audit:

```sql
SELECT actor_account_id, occurred_at, action, target_record_key,
       reason, before_data_json, after_data_json, correlation_id
FROM dbo.audit_logs
WHERE target_table='departments'
  AND action IN ('DEPARTMENT_HEAD_APPOINT','DEPARTMENT_HEAD_END')
ORDER BY audit_id DESC;
```

## Bước 5 Chạy Angular

```powershell
cd C:\NguyenXuanAnh\Git-Nhom-HRM\frontend
npm ci
npm run build
npm start
```

Mở `/departments` trên Angular. Proxy đã đổi sang cổng 2005 và bỏ tiền tố `/api` khi chuyển tiếp tới Spring. Endpoint health hỗ trợ `/health` và `/api/health`; health chỉ kiểm tra HTTP, không khẳng định database còn kết nối.

Chọn phòng → chọn người mới → nhập ngày/lý do → nếu thay người thì chọn chức vụ tiếp theo của người cũ → lưu. Sau thành công giao diện tải lại danh sách, ứng viên và lịch sử. Không đổi dữ liệu hiển thị thành thành công khi backend trả lỗi.

Đăng nhập/JWT của Việt vẫn là điều kiện để thao tác phòng ban qua Web. Chưa có token thì giao diện hiện thông báo 401; không coi đó là đã kiểm thử end-to-end đăng nhập trên trình duyệt.

## Giới hạn và quyết định kỹ thuật

- API mới dùng JdbcTemplate để thay nhiều bản ghi phân công và audit trong một Spring transaction; các API cũ vẫn dùng JPA. Hai cách truy cập cùng datasource/transaction, không tạo backend hoặc DB thứ hai.
- Khóa `HRM_ASSIGNMENT_TIMELINE` hiện tuần tự hóa các thao tác ghi phân công trên toàn hệ thống. Phù hợp quy mô đồ án; nếu cần lưu lượng lớn, thiết kế khóa theo nhiều nhân viên/phòng có thứ tự ổn định và kiểm thử lại trước khi thay.
- Trigger là lớp bảo vệ cho đường ghi SQL khác. Các đường ghi hợp lệ ở ứng dụng lấy khóa trước; SQL trực tiếp không tuân thủ thứ tự có thể bị deadlock/timeout và phải retry, không được bỏ ràng buộc chống trùng.
- Migration chưa tự bổ nhiệm nhân viên nào; người dùng chọn người và ngày có căn cứ. Không chuyển mọi chức vụ mang tên gần giống trưởng phòng sang chức vụ hệ thống.
- Các lỗi khác trong REVIEW_XUAN_ANH.md không tự được xem là đã sửa chỉ vì phần trưởng phòng đã triển khai. Bản ZIP cũ không được đóng gói lại hoặc chia sẻ trong công việc này.
