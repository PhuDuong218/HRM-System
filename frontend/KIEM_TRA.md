# Kiểm tra giao diện nhân sự sau khi đồng bộ main

## Phạm vi thay đổi

Giữ nguyên menu phân nhóm và các module của nhóm trên main. Chỉ bổ sung nút thu/mở sidebar, màn hình nhân viên và bố cục danh sách/chi tiết phòng ban. Không sửa backend, database, module hợp đồng, công, lương hoặc báo cáo.

Quy ước giao diện dùng chung (`shared/record-workspace.ts`): danh sách bên trái, chi tiết hoặc biểu mẫu bên phải. Trên màn hình nhỏ, hai phần xếp dọc. Bấm Đóng để trở về danh sách đầy đủ. Thao tác nhạy cảm phải xác nhận; khi đang lưu, khóa thao tác. Lỗi backend hiển thị nguyên thông điệp nghiệp vụ. Không có nút xóa cứng nhân viên.

## Chạy

Trong CMD, mở một terminal cho Spring Boot và một terminal cho Angular:

```bat
cd C:\NguyenXuanAnh\Git-Nhom-HRM\HRM_System
.\mvnw.cmd spring-boot:run
```

```bat
cd C:\NguyenXuanAnh\Git-Nhom-HRM\frontend
npm ci
npm start
```

Mở http://localhost:4200/login và dùng tài khoản ADMIN/HR hiện có. Proxy Angular chuyển API đến localhost:2005; không dùng environment Postman để đăng nhập web. Không ghi mật khẩu vào tài liệu/Git.

## Đối chiếu API

- ADMIN: GET `/employees/search` trả Spring Page (`content`, `number`, `totalPages`, `totalElements`), tìm/lọc phía server, trang đầu là 0.
- HR: GET `/employees` trả mảng đã giới hạn theo quyền backend. Backend hiện chặn HR ở API search/phân trang, vì vậy UI không giả lập chức năng tìm kiếm phân trang server cho HR.
- GET `/employees/{id}/detail`, POST `/employees`, PUT `/employees/{id}`, PATCH `/employees/{id}/contact`.
- POST `/employees/{id}/terminate` là thôi việc, giữ hồ sơ; có xác nhận trên UI. DTO hiện chưa nhận lý do, không tự thêm trường giả.
- GET `/departments`, `/positions` lấy danh mục thật. API chức vụ yêu cầu authority `position.view`, không chỉ dựa vào tên role ADMIN.
- Các API trưởng phòng và lịch sử vẫn giữ nguyên DepartmentService hiện có.

## Kết quả đã xác nhận ngày 30/10/2026

- Build Angular production thành công (TypeScript/template strict).
- Đăng nhập qua proxy thật thành công với tài khoản ADMIN thử nghiệm.
- Search nhân viên trả 10 bản ghi/trang, tổng 198; detail trả đúng employeeId.
- Danh mục phòng ban trả 61 bản ghi.
- Danh mục chức vụ trả 403 với tài khoản thử nghiệm. Cần người quản lý quyền đối chiếu/cấp `position.view` theo ma trận nhóm; UI hiển thị lỗi và không tự vượt quyền.
- Không chạy các thao tác ghi/thôi việc trên dữ liệu hiện có trong lượt này. Chưa xác nhận tương tác/hiển thị desktop-mobile bằng trình duyệt; build/API không thay thế nghiệm thu giao diện.

## Checklist thao tác cần nghiệm thu trên dữ liệu thử

- [ ] Thu/mở sidebar trên desktop; menu mobile vẫn hoạt động; nhóm menu và hồ sơ/đổi mật khẩu của nhóm còn nguyên.
- [ ] Mở Nhân viên → Chi tiết: bảng vẫn hiện, dòng đang chọn nổi bật, đóng panel được.
- [ ] Chuyển nhanh giữa hai nhân viên: phản hồi cũ không ghi đè hồ sơ mới.
- [ ] ADMIN tìm từ khóa/lọc trạng thái/phòng ban, đổi lọc về trang đầu, kết quả rỗng có thông báo.
- [ ] Thêm nhân viên có mã trùng, thiếu phòng/chức vụ, sai email/điện thoại: báo lỗi, giữ dữ liệu form để sửa.
- [ ] Lưu hồ sơ/liên hệ thành công: tải lại danh sách và chi tiết từ API; nút lưu khóa trong lúc gửi.
- [ ] Thôi việc trên nhân viên thử: bắt buộc xác nhận, backend kiểm tra nghiệp vụ, giữ lịch sử.
- [ ] Mở Phòng ban: chi tiết/bổ nhiệm/kết thúc/lịch sử nằm trong panel bên phải; nút Đóng hoạt động.
- [ ] Backend tắt, phiên hết hạn, 403 và 404: thông báo có nghĩa; 401 điều hướng login qua interceptor hiện có.

## Các phần chưa được coi là hoàn tất

Routes chức vụ, trình độ, đơn từ và một số module khác vẫn là Placeholder theo main hiện tại. Bố cục dùng chung đã sẵn sàng nhưng không có nghĩa mọi FE-001..009 đã nghiệm thu. Không xóa placeholder hoặc module của các thành viên khác để che phần chưa tích hợp.

## Cập nhật panel và sidebar

- Sidebar: nút mũi tên gắn cạnh danh mục, hướng trái khi đang mở, hướng phải khi thu gọn. Không đặt nút thu gọn trong topbar nữa. Mobile giữ nút Menu hiện có.
- Nhân viên, phòng ban và hợp đồng dùng chung RecordWorkspace. Danh sách bên trái; chi tiết/thêm/sửa và thao tác liên quan bên phải. Màn hình <=1250px dùng drawer bên phải có nền che, không đẩy form xuống cuối bảng.
- Nút Đóng, phím Escape hoặc bấm nền che đóng panel; khóa đóng khi đang gửi dữ liệu. Tab giới hạn trong panel khi đang ở chế độ drawer.
- Hợp đồng chỉ đổi bố cục; giữ nguyên nghiệp vụ/API của module nhóm.
- Chưa áp dụng vào các route Placeholder vì source chưa có màn hình nghiệp vụ. Không tự tạo dữ liệu hoặc form giả.

Nghiệm thu bổ sung: mở từng màn hình, thử Chi tiết và Thêm; xem danh sách còn bên trái trên desktop; thu hẹp màn hình và kiểm tra drawer hiện ngay. Thử thu/mở menu liên tiếp, kiểm tra mũi tên và các nhóm menu giữ đúng trạng thái. Các thao tác trình duyệt này chưa được xác nhận tự động.

## Rà soát hrm_db và tái tuyển — 30/10/2026

Thay đổi: mũi tên nằm trong sidebar; khi thu chỉ còn ô mũi tên. Hợp đồng dùng bộ chọn nhân viên từ API thật ở cả lọc và tạo; bỏ nút Đóng trùng trong nội dung chi tiết.

Backend runtime mặc định dùng profiles `local,hrm-db`; profile hrm-db chỉ đổi URL database, giữ credentials local và ddl-auto=none. Test vẫn dùng cấu hình test riêng, không chạy test ghi dữ liệu trên hrm_db.

Đã chạy migration 001, 002, 003 có sẵn trên hrm_db. Đã đối chiếu hai tài khoản mẫu: EMP033 là ADMIN → CNTT/QT; EMP034 là HR → HCNS/CVNS, phân công từ hire_date 01/01/2026. Chỉ thêm khi chưa có lịch sử và ghi audit EMPLOYEE_ASSIGNMENT_REPAIR; không xóa bản ghi.

Luồng mới POST `/employees/{id}/rehire`: ADMIN có EMPLOYEE_MANAGE; ngày tiếp nhận sau ngày nghỉ, không ở tương lai; phòng/chức vụ hoạt động, không bổ nhiệm thẳng trưởng phòng; chặn phân công còn mở/chồng ngày và đơn nghỉ việc chưa hoàn tất. Giữ ngày vào làm đầu tiên, audit ngày nghỉ cũ, tạo phân công mới. Không tự mở tài khoản, hợp đồng hoặc cấp lại quỹ phép. HR chưa có luồng tái tuyển có kiểm soát phạm vi, nên nút hiện cho ADMIN.

Kết quả đã xác nhận:
- Frontend production build thành công.
- hrm_db: 34 nhân viên; không trùng phân công hiện tại/trưởng phòng; không có khoảng phân công đảo ngày; không có tài khoản đang active gắn nhân viên đã nghỉ.
- Backend probe kết nối hrm_db cổng 2009: khởi động thành công, `/api/health` trả UP. Phiên probe tắt tác vụ cập nhật nền. Không xác nhận đăng nhập bằng mật khẩu của hrm_db trong lần probe này.
- 44/44 test tái tuyển unit và đơn từ SQL Server đã pass sau khi thêm JWT test và tắt startup job khi Clock bị mock.
- Lượt tổng cuối: 553 tests, 490 pass, 17 failures, 5 errors, 41 skipped. Chưa đủ điều kiện kết luận toàn hệ thống đã pass.

Các nhóm cần xử lý tiếp (không nới quyền/đổi nghiệp vụ để làm xanh test):
- AttendanceServiceIntegrationTest và AttendanceWorkflowIntegrationTest phụ thuộc lịch/tài khoản fixture cố định đang thiếu hoặc không đúng trạng thái trong hrm_test.
- ModuleRepositoryIntegrationTest tạo trùng payroll_periods vì entity sau merge đã tạo bảng này.
- G1EndpointAccessTest dùng standalone MockMvc, không bật method security nhưng kỳ vọng @PreAuthorize chạy.
- EmployeeServiceMergeTest còn kỳ vọng bỏ qua identityNumber, khác DTO cập nhật hiện tại.
- WorkShiftDurationTest, WorkShiftValidationTest, WorkShiftValidCasesTest vẫn gọi API ca làm cũ; service đã yêu cầu API có chi tiết giờ nghỉ (409).

Logs nằm trong HRM_System/target/system-review-final-tests.log, personnel-review-fixed-tests.log và rehire-final-tests.log. target không đưa lên Git.

Chạy lại backend sau khi dừng phiên cũ bằng Ctrl+C:

```bat
cd /d C:\NguyenXuanAnh\Git-Nhom-HRM\HRM_System
.\mvnw.cmd spring-boot:run
```

Giữ JWT_SECRET đã đặt trong cửa sổ CMD. Log phải ghi hai profiles local và hrm-db và JDBC databaseName=hrm_db. Tài khoản Postman được sinh riêng trong hrm_test không tự tồn tại trong hrm_db; dùng tài khoản thật của hrm_db, không reset mật khẩu hàng loạt.

Kiểm tra bổ sung cuối: 13/13 ca tái tuyển (12 unit + 1 SQL Server integration với fixture rollback) pass. Kiểm tra lại hrm_db sau sửa hai mẫu: active_without_assignment=0. Backend probe cổng 2009 đã được tắt sau khi health UP; phiên 2005 đang chạy trước đó cần restart để nhận source/profile mới. Khi thu sidebar, rail rộng 0, chỉ ô mũi tên nổi còn hiển thị.

## ADMIN: kiểm tra truy cập và CRUD ngày 30/10/2026

Đã đăng nhập admin.sample bằng mật khẩu mẫu được người dùng cung cấp; không reset mật khẩu.

Lỗi thực tế: AuthenticatedAccount chưa implements Principal, nên Authentication#getName trả chuỗi record thay vì username, làm PersonnelAccessService không tìm thấy tài khoản và trả 403. Đã triển khai Principal#getName trả username để thống nhất các module. Giữ kiểm tra quyền và trạng thái tài khoản hiện có.

Menu đổi từ một expandedGroup sang Set expandedGroups; mở một nhóm không đóng nhóm khác, chuyển trang tự mở nhóm hiện tại và giữ các nhóm đang mở.

Frontend mới: positions, qualifications, leave-types nối API thật, có danh sách/phân trang/chi tiết/thêm/sửa/ngừng sử dụng. Ngừng sử dụng giữ lịch sử. Form bên phải qua RecordWorkspace; mã không sửa sau tạo.

Backend kiểm tra riêng cổng 2009 kết nối hrm_db với tác vụ cập nhật nền bị tắt. 43/43 kiểm tra HTTP và trạng thái pass:
- ADMIN đọc employees, departments, positions, qualification-levels, leave-types, employee-assignments, employee-requests, leave-entitlements: 200.
- Cùng 8 đường dẫn khi không có token: 401.
- Chức vụ, trình độ, loại nghỉ: tạo 201, đọc 200, mã trùng 409, thiếu trường 400, sửa 200, ID không có 404, ngừng sử dụng 200/204, đọc lại được và isActive=false.
- Chỉ tạo 3 bản ghi mã QA ngẫu nhiên rồi ngừng sử dụng. Các bản ghi này và audit vẫn được giữ trong hrm_db; không hard delete.

Kết quả HTTP trong HRM_System/target/admin-crud-results.json; log test trong target/admin-access-tests.log. Không đưa target lên Git.

Phạm vi chưa hoàn tất giao diện: đơn từ hiện còn Placeholder; phân công/điều chuyển, bằng cấp theo nhân viên, quỹ phép và audit chưa có màn hình đầy đủ dù API đã đọc được. Không coi API 200 là đã nghiệm thu toàn bộ các thao tác web của 11 mục. Cần hoàn thiện giao diện và kiểm thử thêm workflow trước khi bàn giao toàn bộ task.

## Giao diện quản trị đủ các mục nhiệm vụ — cập nhật cuối

Các ghi chú Placeholder ở phần trước là lịch sử trước triển khai. Hiện đã nối các route thuộc nhiệm vụ Xuân Anh:

| Mục | Đường dẫn | Thao tác |
|---|---|---|
| Nhân viên | /employees | tìm/lọc/phân trang, chi tiết, thêm, sửa hồ sơ/liên hệ, thôi việc, ADMIN tiếp nhận lại |
| Phòng ban | /department-catalog | danh sách, thêm, sửa, lọc, ngừng sử dụng |
| Trưởng phòng | /departments | bổ nhiệm/thay người/kết thúc, ứng viên, lịch sử |
| Chức vụ | /positions | tìm/lọc, thêm, sửa, ngừng sử dụng |
| Phân công/điều chuyển | /assignments | xem tất cả hoặc lịch sử nhân viên, chọn phòng/chức vụ/ngày và nhập lý do điều chuyển |
| Trình độ | /qualifications | thêm/sửa/ngừng sử dụng danh mục |
| Bằng cấp/chứng chỉ | /certificates | chọn nhân viên, xem/thêm/sửa, chọn trình độ chính, gỡ mềm |
| Loại nghỉ | /leave-types | thêm/sửa/ngừng sử dụng, chính sách hưởng lương và trừ quỹ riêng |
| Quỹ phép | /leave-entitlements | cấp/điều chỉnh, lọc nhân viên, số giờ cấp/đã dùng/còn lại |
| Đơn nghỉ phép/nghỉ việc | /requests | lọc trạng thái/loại, chi tiết/phân bổ giờ, duyệt/từ chối, thực thi nghỉ việc đến hạn cho ADMIN |
| Audit | /audit | tra cứu theo đối tượng/bản ghi, so sánh trước/sau; ADMIN lọc thêm tài khoản và khoảng UTC qua API trung tâm |

Trong menu Phòng ban, nút “Thêm / sửa phòng ban” mở danh mục phòng ban. Các màn hình dùng RecordWorkspace: trái là danh sách, phải là chi tiết/form; màn hình nhỏ dùng drawer. HTTP tập trung trong các service. API schema của các DTO mới nằm trong models/personnel.model.ts. Forms và dropdown lấy dữ liệu backend thật, không tạo danh sách mẫu.

Phạm vi Angular theo bảng phân công nhóm là cổng ADMIN/HR quản lý. Nhân viên tự nộp/rút đơn trên ứng dụng nhân viên, không thêm cổng EMPLOYEE vào Angular.

### Xác nhận đã chạy

- npm run build: thành công, TypeScript/template strict.
- 53/53 test hồi quy: EmployeeRequestWorkflowIntegrationTest (32), EmployeeAssignmentValidityIntegrationTest (5), AuthenticatedAccountNameTest (1), PositionPermissionCompatibilityTest (3), EmployeeRehireServiceTest (12). Log: HRM_System/target/frontend-workflow-regression.log.
- Kiểm tra trình duyệt thật qua localhost:4200: đăng nhập ADMIN; chọn EMP001 xem bằng cấp thật; mở panel chi tiết bên phải; mở đồng thời các nhóm menu; tải danh sách 10 đơn; nút từ chối khóa khi chưa có lý do; bấm duyệt nghỉ việc khi thiếu ngày bị chặn trước khi gửi; tra cứu audit mở rộng tải được lịch sử thật.
- Không duyệt/từ chối/nghỉ việc các hồ sơ thật để kiểm tra giao diện. Test workflow ghi dữ liệu dùng hrm_test/rollback theo suite.
- Lý do điều chuyển được thêm tương thích: record DTO vẫn có constructor 3 tham số cho source cũ; reason mới tùy chọn với API cũ, bắt buộc trong form mới; audit ghi reason khi có. Không giả lập reason chỉ ở UI.

### Tự nghiệm thu ghi dữ liệu trên hồ sơ thử

1. Tạo nhân viên mã thử, chọn phòng ban/chức vụ thường; sửa liên hệ, mã trùng và số điện thoại sai phải báo lỗi.
2. Điều chuyển nhân viên thử: ngày hợp lệ tạo phân công mới, giữ phân công cũ. Thử ngày chồng lấn, nhân viên đã nghỉ, chọn chức vụ trưởng phòng trực tiếp đều phải bị backend chặn.
3. Thêm bằng cấp; thử hai trình độ chính, đổi chính và gỡ; kiểm tra không mất audit.
4. Cấp quỹ phép; thử số giờ <=0, ngày ngược, kỳ trùng, hạ mức dưới số đã dùng.
5. Chọn đơn thử; thiếu lý do từ chối hoặc thiếu ngày duyệt nghỉ việc không gửi. Sau duyệt, không thể duyệt lần hai; lỗi 409 phải hiển thị và giữ dữ liệu để đối chiếu.
6. Đơn nghỉ việc tương lai không làm nghỉ ngay. Thực thi đến hạn báo riêng số thành công và lý do BLOCKED; không báo thành công chung cho đơn bị chặn.
7. Kiểm tra audit theo bản ghi, tài khoản, thời gian UTC (mốc kết thúc loại trừ); không hiển thị snapshot thành JSON thô.
8. Dùng HR thiếu quyền: backend phải trả 403, UI hiển thị lý do; thử thiếu token/hết phiên phải về login. Không coi 53 test hay build là chứng minh mọi quyền HR đã nghiệm thu.

### Các giới hạn của backend hiện tại

- Bằng cấp hiện có năm tốt nghiệp, số chứng chỉ, nơi cấp; API chưa có ngày cấp/ngày hết hạn. UI không tự gửi/lưu các trường không tồn tại.
- API hồ sơ/liên hệ nhân viên hiện chưa có reason; UI không tạo trường lý do giả. Lý do đã có ở điều chuyển, trưởng phòng, đơn từ và audit theo DTO tương ứng.
- Cập nhật 31/10: HR đã có tìm kiếm/phân trang nhân viên theo phạm vi server. Tái tuyển vẫn dành cho ADMIN theo API hiện hành.
- Các lỗi test toàn hệ thống ở module nhóm được ghi ở phần trước chưa được coi là đã sửa trong lượt frontend này. Không tuyên bố toàn bộ hệ thống 100% pass.

Chốt kiểm tra: 2/2 EmployeeTransferReasonTest pass (lý do đến audit và constructor cũ tương thích). Backend cuối đã khởi động thành công trên 2005/hrm_db với source mới. Đăng nhập ADMIN và kiểm tra 10 API đọc cho nhân viên, phòng ban, chức vụ, trình độ, loại nghỉ, phân công, bằng cấp, quỹ phép, đơn từ, audit trung tâm đều trả 200. Sau khi backend được khởi động lại, cần đăng nhập lại trên web để nhận phiên mới. Không cần mở thêm backend thứ hai.

## Responsive và phân trang thống nhất

- Menu <=800px là drawer fixed cao 100dvh; có hamburger ở topbar sticky, nút đóng riêng, nền che và Escape. Chọn mục tự đóng; không còn đưa menu vào luồng trang để đẩy nội dung xuống. Menu giữ cuộn riêng, chặn cuộn nền khi mở và hỗ trợ Tab trong menu.
- Nút thu desktop cách mép phải 28px, tránh vùng scrollbar; desktop vẫn thu vào chỉ còn mũi tên.
- Pagination dùng chung cho nhân viên, phòng ban/lịch sử/ứng viên, hợp đồng, danh mục và workspace nghiệp vụ. Cùng nhãn Trang x/y, nút Trước/Sau, chiều cao chạm 44px; khóa trong lúc tải/lưu và ở đầu/cuối.
- Bảng điện thoại cuộn ngang trong khung, có gợi ý vuốt; không ép cả trang tràn ngang.

Đã kiểm tra trình duyệt thật: 390x844 mở menu, chọn Điều chuyển tự đóng. Menu position=fixed, top=0, cao844; sau đóng mainTop khoảng64.8px ngay dưới topbar, menu visibility=hidden. Kiểm tra 320/768/1280px: document scrollWidth lần lượt305/753/1265, đều không vượt viewport; menu mobile chỉ hiện <=800px. Nhân viên bấm Sau chuyển đúng Trang2/4 và dữ liệu đổi. Không ghi database trong lượt sửa responsive.

## Kiểm tra sau merge ngày 31/10/2026

Các ghi nhận trong mục này là kết quả mới nhất; các kết quả ở phần trước là lịch sử kiểm tra.

### Phạm vi thay đổi

- Đã ghép conflict của `app.routes.ts` và `layout/admin-layout.html`, giữ giao diện/menu/ngôn ngữ/theme và route ca làm, lịch, chấm công, tổng hợp công, tăng ca mới của nhóm. Hai file đã đánh dấu resolved; chưa commit hoặc push merge.
- `ManagementPageBase` dùng chung trạng thái tải/lưu/lỗi, xác nhận có dịch và vòng đời lệnh ghi. EmployeesPage, DepartmentsPage, CatalogPage và PersonnelWorkspacePage kế thừa. `PageHeader`, `RecordWorkspace`, `Pagination` là các component chung cho tiêu đề, danh sách/chi tiết và phân trang.
- `TranslatePipe` + `LanguageService` dùng chung từ điển, đổi ngôn ngữ ngay trên menu, tiêu đề, nhãn, trạng thái rỗng, biểu mẫu, phân trang và các trang chấm công mới. Tên nhân viên, tên phòng, nội dung người dùng nhập và thông báo chưa có bản dịch từ backend vẫn giữ nguyên dữ liệu gốc.
- Proxy giữ `/api/management`, `/api/auth`, `/api/me`, `/api/admin`, `/api/overtime`, `/api/attendance`, `/api/reports`, `/api/health`; nhóm endpoint nhân sự cũ vẫn bỏ tiền tố `/api`. Phải restart Angular khi sửa proxy.

### Quyền theo ma trận đã xác nhận

- ADMIN quản lý thông tin công việc toàn trung tâm. HR chỉ quản lý nhân viên/phòng ban được giao qua `hr_department_scopes`; phạm vi kiểm tra ở server, không tin ID/vai trò client tự gửi.
- Danh sách/phân trang nhân viên, phòng ban, phân công, quỹ phép và đơn quản lý lọc phạm vi trước khi trả kết quả và tổng số bản ghi.
- CCCD và địa chỉ nhà chỉ chủ hồ sơ được đọc. API chi tiết lẫn snapshot audit đều lọc các trường này đối với người khác, kể cả ADMIN. Form sửa người khác không gửi lại giá trị private đang bị ẩn; PUT giữ nguyên CCCD đã lưu.
- Không tự sửa liên hệ: chặn PATCH tự sửa và PUT đổi email/điện thoại chính mình. Thông tin công việc vẫn xem/sửa theo quyền nghiệp vụ.
- Tài khoản/phân quyền và trang cấu hình kết nối chỉ ADMIN, có guard và lọc menu; backend vẫn là nơi quyết định quyền thực tế.
- Không thay đổi quy trình duyệt đơn hay tự cấp quyền mới cho HR. Các module lương/công của nhóm được giữ; việc ghép giao diện không phải chứng nhận toàn bộ ma trận module khác đã nghiệm thu.

### Kết quả kiểm thử

1. `PersonnelScopePrivacyIntegrationTest`: 14/14 đạt trên SQL Server `hrm_test`, dùng JWT và security filter thật. Test có giao dịch rollback và kiểm tra DB_NAME trước khi tạo fixture.
2. `EmployeeTransferReasonTest` + `AuthenticatedAccountNameTest`: 3/3 đạt. Bộ kiểm tra cuối gồm riêng tư, hồ sơ, lý do điều chuyển và tên Principal: 22/22 đạt.
3. Hồi quy 15 lớp nhân sự/đơn từ/trưởng phòng: 111/112 đạt ban đầu. Một lỗi cũ ở overload `updateEmployee(EmployeeRequestDTO)` làm thay đổi CCCD trái hợp đồng legacy đã sửa. Chạy lại `EmployeeServiceMergeTest`: 5/5 đạt; các lớp còn lại đã đạt ở lần hồi quy đó.
4. Build Angular production đạt. Các trang nghiệp vụ được lazy-load để giảm gói khởi động.
5. Trình duyệt thật trên `hrm_db`: đăng nhập ADMIN thành công; tải danh sách 34 nhân viên, xem chi tiết cạnh danh sách và thông báo private; mở chức vụ, phòng ban, trình độ, điều chuyển, bằng cấp, loại nghỉ, quỹ phép, đơn và audit; kiểm tra đổi VI/EN. Không dùng CRUD thử trên hồ sơ thật để chứng minh nghiệp vụ.

Log local: `HRM_System/target/merge-personnel-policy-tests.log`, `merge-personnel-regression.log`, `merge-profile-regression.log`, `frontend/.angular/merge-build.log`. Không commit log, tài khoản/mật khẩu hay file cấu hình local.

### Tự chạy và nghiệm thu

1. Khởi động backend tại 2005 với profile/config database đã chọn. Khi dùng `hrm_db`, đặt `HRM_DB_URL` trỏ rõ `databaseName=hrm_db`; không dựa vào tên profile để đoán database. Giữ mật khẩu SQL trong cấu hình local hoặc biến môi trường.
2. Tại `frontend`, chạy `npm start`, mở `http://localhost:4200/login`, đăng nhập tài khoản được nhóm cấp. Sau restart backend cần đăng nhập lại vì phiên JWT có thể hết hiệu lực.
3. Chuyển VI/EN, mở từng mục ở trên, mở chi tiết, đóng bằng nút hoặc Escape; danh sách và bộ lọc phải được giữ. Thử từ khóa không tồn tại rồi xóa lọc. Đổi bộ lọc phải về trang đầu.
4. Mở Thêm mới nhưng để trống trường bắt buộc: Lưu bị khóa, không tạo dữ liệu. Kiểm tra ngày ngược, email/số điện thoại sai, gửi lặp, bản ghi bị người khác thay đổi; hiển thị lỗi backend và không báo thành công khi API thất bại.
5. Kiểm tra HR trong/ngoài scope, ADMIN xem hồ sơ người khác không thấy CCCD/địa chỉ; nhân viên gọi API hồ sơ riêng thấy dữ liệu mình, không thấy người khác. Luồng nhân viên tự phục vụ vẫn thuộc ứng dụng theo phân công nhóm, không tự mở rộng web quản lý cho EMPLOYEE.
6. Dữ liệu tạo/sửa/duyệt/tái tuyển thử dùng `hrm_test` hoặc hồ sơ thử do nhóm xác nhận; không chạy các test fixture vào `hrm_db`.

Ghi nhận responsive cuối: viewport yêu cầu 390/320 được trình duyệt báo kích thước CSS thực tế 434/356; document scrollWidth lần lượt 434/356, không tràn ngang. Menu là fixed và khi chọn mục tự đóng, main bắt đầu khoảng 64.9px dưới topbar. Biểu mẫu danh mục trống khóa Lưu.

Backend do lượt kiểm tra này khởi động trên cổng 2005/hrm_db có tạm tắt ba job tự động (hết hạn hợp đồng, thực thi nghỉ việc đến hạn, quét thiếu checkout) để tránh ghi dữ liệu nền lúc đối chiếu. Đây là tham số của tiến trình kiểm tra, không sửa cấu hình mặc định. Khi chuyển sang chạy nghiệp vụ bình thường, dừng tiến trình kiểm tra rồi chạy backend theo lệnh thông thường của nhóm để bật lại các job theo cấu hình gốc.

## Chuẩn hóa nút và đối chiếu quyền ADMIN — 31/10/2026

### Kết luận về hai nút người dùng phản ánh

- Bằng Đại học của EMP002 đã có `isPrimary=true`. Thao tác chọn lại không cần thiết; UI hiện nhãn “Đang là trình độ chính” và hướng dẫn mở bằng khác để đổi. ADMIN và HR trong scope chọn được bằng chưa là chính; backend hạ bằng cũ, chỉ giữ tối đa một bằng chính, ghi audit. Kiểm thử dùng hai bằng và cả hai vai trò đã đạt.
- Nút điều chuyển trước đây bị khóa khi chưa chọn bộ lọc nhân viên, làm người dùng hiểu là thiếu quyền. Nay có thể mở form từ nút đầu trang hoặc từ dòng phân công, chọn nhân viên đang làm trong form. Employee ID được dùng ở URL, không thêm trường ngoài DTO chuyển phòng/chức vụ.
- ADMIN quản lý nhân sự toàn trung tâm; HR cần quyền quản lý và scope được giao. EMPLOYEE/trưởng phòng không mặc nhiên có quyền điều chuyển. Trưởng phòng đang giữ nhiệm kỳ phải đi luồng thay/kết thúc nhiệm kỳ ở Phòng ban, không chuyển thẳng bằng thao tác điều chuyển thường. Không đổi quyền để bỏ qua quy tắc này.
- Quy tắc CCCD/địa chỉ chỉ chủ hồ sơ đọc và không tự sửa liên hệ vẫn giữ nguyên. Không cấp thêm permission hoặc sửa dữ liệu nhân sự thật để làm nút sáng lên.

### Quy ước dùng chung

`src/app/shared/management-page.css` áp dụng qua class của ManagementPageBase: bộ lọc chia cột đều; nút tìm/xóa lọc/tải lại nằm trên hàng riêng, căn phải. Tiêu đề danh sách, nút chi tiết, nhóm thao tác và hàng Hủy/Lưu dùng cùng cách bố trí. Nút nhóm chính cao 42px ở desktop, nút trên điện thoại ít nhất 44px. Thao tác xóa/ngừng sử dụng tách khỏi nút chính, có xác nhận theo luồng hiện có.

Các trang áp dụng: Nhân viên; Phòng ban/trưởng phòng; Danh mục phòng ban; Chức vụ; Trình độ; Phân công/điều chuyển; Bằng cấp/chứng chỉ; Loại nghỉ; Quỹ phép; Đơn nghỉ phép/nghỉ việc; Audit. Nghiệp vụ module của thành viên khác được giữ.

### Sửa lỗi giao diện/nghiệp vụ liên quan

- Caption ẩn của bảng nhân viên được đặt absolute đúng cách, không làm cả trang tràn ngang trên điện thoại; bảng vẫn cuộn trong khung.
- Hủy sửa danh mục/bằng cấp khôi phục bản sao từ dữ liệu gốc, không hiển thị hoặc giữ lại nội dung chưa lưu.
- Kỳ phép theo API là `[periodStart, periodEnd)`: nhãn đổi thành “Đến trước ngày”, có hướng dẫn; ngày kết thúc bằng/trước ngày bắt đầu khóa Lưu. Khi sửa quỹ phép, nhân viên và loại nghỉ không đổi được, đúng hợp đồng backend.
- Đơn nghỉ việc chờ duyệt yêu cầu ngày làm cuối trước khi bấm Duyệt; từ chối yêu cầu lý do. Backend vẫn quyết định cuối cùng về quyền, trạng thái và xung đột.

### Kiểm thử đã thực hiện

- Bộ hồi quy nhân sự/đơn từ/trưởng phòng: 105/105 test đạt.
- Bổ sung kiểm tra HTTP thật trên SQL Server + JWT: PersonnelScopePrivacyIntegrationTest 20/20 đạt, gồm chọn/chuyển bằng chính, CRUD 4 danh mục, tạo/sửa/liên hệ/nghỉ việc nhân viên, điều chuyển ADMIN/HR, quỹ phép, lỗi 400/403/404/409, không xóa lịch sử và bảo vệ dữ liệu riêng tư. Bộ 20 có 18 test nằm trong bộ 105; tổng cộng 107 test khác nhau đã chạy đạt trong lượt này.
- CRUD thử chạy trong hrm_test, mỗi fixture có rollback; không tạo/duyệt/nghỉ việc thử trên hồ sơ thật trong hrm_db.
- Trình duyệt ADMIN: 11 màn hình × 1440/768/390/320 = 44 phép đo, không tràn ngang trang và không có cảnh báo lỗi API sau khi tải xong. Bộ nút lọc cùng hàng có cùng mốc đáy. Các bảng rộng cuộn bên trong khung.
- Kiểm tra form thêm của 7 loại trang: Hủy/Lưu cùng hàng, nút Lưu khóa khi thiếu bắt buộc. Kiểm tra riêng trạng thái bằng chính của EMP002, Hủy sửa danh mục không lưu, và kỳ phép bằng ngày bị chặn trên UI.
- Build Angular production thành công. File số đo local: `.angular/personnel-layout-measurements.json`, `.angular/personnel-form-measurements.json`; log backend `target/personnel-actions-regression.log`, `target/personnel-crud-http-tests.log`. Không commit log/ảnh/số đo local hoặc thông tin tài khoản.
