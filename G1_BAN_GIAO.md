# Bàn giao backend G1 — Xuân Anh

Phạm vi được Xuân Anh xác nhận: **nhân viên, phòng ban, chức vụ, điều chuyển**. Bao gồm trưởng phòng, quyền quản lý, audit và kiểm thử các luồng này. Không bao gồm giao diện Angular hoàn chỉnh, JWT/đăng nhập của Việt, bằng cấp, nghỉ phép hoặc quy trình duyệt đơn nghỉ việc.

## 1. Chạy test tự động

Mở terminal PowerShell, không mở file `.ps1` bằng double-click:

```powershell
cd C:\NguyenXuanAnh\Git-Nhom-HRM\HRM_System
powershell -NoProfile -ExecutionPolicy Bypass -File .\run-g1.ps1 -Mode test
```

Cần SQL Server hoạt động, database `hrm_test`, cấu hình local hợp lệ và JDK 21 hoặc cao hơn. Script tự tìm Maven có sẵn hoặc Maven trong wrapper cache, tránh lỗi wrapper trên máy hiện tại. Có thể chỉ định `-JavaHome`, `-MavenCommand`, `-MavenRepository`, hoặc `-Offline` nếu cần. Không ghi mật khẩu vào script.

Kết quả đã xác minh ngày 26/10/2026: **84 test, 0 thất bại, 0 bỏ qua**. Bảng từng test được xuất vào `HRM_System/target/g1-test-summary.csv`; kết quả JUnit ở `target/surefire-reports`. Phải xem kết quả của lần chạy mới; báo cáo cũ không chứng minh code mới đã đạt.

## 2. Chạy server dành riêng cho Postman

Trong terminal tại `HRM_System`:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\run-g1.ps1 -Mode postman
```

Chờ dòng **G1_POSTMAN_READY**. Giữ terminal này chạy. Server chỉ nghe `127.0.0.1:2006`, tự dừng sau 30 phút; có thể thêm `-DurationMinutes 60` (tối đa 120).

Đây là server test nằm trong `src/test`, không nằm trong jar ứng dụng. Nó dùng chính controller/service/database của G1, chỉ bổ sung phiên đăng nhập kiểm thử bằng token ngẫu nhiên trong bộ nhớ. Nó **không triển khai JWT** và không thay cơ chế đăng nhập của nhóm.

Server tự tạo hoặc tái sử dụng đúng hai fixture dành riêng: `G1-POSTMAN-ADMIN`, `G1-POSTMAN-EMPLOYEE`; tài khoản tương ứng `g1.postman.admin`, `g1.postman.employee`. Chúng có role thật trong `hrm_test`, dùng mật khẩu băm giả chỉ phục vụ fixture; không dùng chúng để đăng nhập hệ thống. Server không sửa tài khoản của bạn. Khi dừng bình thường, hai tài khoản test bị khóa; hồ sơ vẫn giữ để bảo toàn liên kết audit. Nếu máy/tiến trình bị tắt đột ngột, khởi động rồi dừng lại server test để thực hiện bước khóa này.

## 3. Import vào Postman

1. Import collection: **`HRM_System/test-tools/g1.postman_collection.json`**.
2. Import environment do server vừa sinh: **`HRM_System/target/g1-postman-runtime.postman_environment.json`**.
3. Chọn environment **HRM G1 — local test session** ở góc trên Postman. Không để `No Environment`.
4. Trong environment, đặt giá trị hiện tại của **`runMutations` thành `true`** nếu muốn chạy toàn bộ chuỗi tạo/sửa/điều chuyển/nghỉ việc.
5. Chạy cả collection, **1 iteration**, đúng thứ tự folder 00 → 04, không chạy song song các request trong chuỗi.

Mỗi lần khởi động lại server, token thay đổi. Phải import lại environment runtime; không dùng token của lần trước. Không chia sẻ environment runtime chứa token phiên test. File template `test-tools/g1.postman_environment.json` không có token, chỉ dùng làm mẫu cho môi trường khác.

Collection có **149 request**, gồm:

- Health.
- Chưa đăng nhập: API quản trị phải trả 401; header tự khai role/account không cấp quyền.
- Tài khoản EMPLOYEE: API quản trị phải trả 403.
- Đọc danh sách, tìm kiếm, lọc, phân trang; 400/404 cho tham số và đối tượng không hợp lệ.
- Chuỗi ghi dữ liệu: CRUD danh mục, hồ sơ, PATCH/PUT, điều chuyển, lịch sử, trưởng phòng, audit và nghỉ việc.

Kết quả HTTP 401/403/409 ở request được đặt tên tương ứng là kết quả mong đợi. Xem assertion màu xanh, không chỉ nhìn mã HTTP.

Đã chạy collection bằng Newman trên server test với actor ADMIN/EMPLOYEE thật: **149 request, 176 assertion, 0 lỗi**. Báo cáo chi tiết ở `HRM_System/target/g1-postman-report.json` và log `target/g1-newman.log`; báo cáo raw có thể chứa token phiên test nên không đưa vào Git hoặc chia sẻ công khai.

Mặc định `runMutations=false`: folder 04 bị bỏ qua để không tự ghi dữ liệu. Bỏ qua không phải đã test đạt folder đó. Muốn nghiệm thu đủ phải bật `true` và chạy từ đầu.

Chuỗi ghi tạo **2 phòng ban, 2 chức vụ và 3 nhân viên** có mã `G1PM-...` riêng mỗi lần chạy trong `hrm_test`. Nhân viên được xử lý nghỉ việc ở cuối chuỗi, hồ sơ/lịch sử/audit vẫn giữ. Đây là HTTP thật nên dữ liệu được commit; không DELETE dọn hoặc tắt trigger. Các integration test Java dùng rollback và không để lại các fixture nghiệp vụ của chúng.

## 4. Dừng server test

Ở terminal thứ hai, tại `HRM_System`:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\run-g1.ps1 -Mode stop
```

Chờ terminal server kết thúc. Lần chạy sau cần khởi động lại và import environment mới.

## 5. Các chức năng đã triển khai

| Module | Chức năng |
| --- | --- |
| Nhân viên | Tạo kèm phân công ban đầu; đọc danh sách và chi tiết; tìm tên/mã, lọc trạng thái/phòng/chức vụ; phân trang; PUT hồ sơ; PATCH liên hệ; nghỉ việc trực tiếp giữ hồ sơ |
| Phòng ban | Tạo, đọc, sửa, ngừng dùng/kích hoạt lại; tìm mã/tên, lọc hoạt động, phân trang; mã bất biến, chặn mã trùng; chặn ngừng dùng khi còn phân công hiện tại/tương lai |
| Trưởng phòng | Xem theo ngày, ứng viên và lịch sử phân trang; bổ nhiệm, thay người, kết thúc nhiệm kỳ; bắt buộc lý do, lưu lịch sử và audit; không dùng điều chuyển thường để vượt nghiệp vụ |
| Chức vụ | Tạo, đọc, sửa, ngừng dùng/kích hoạt lại; tìm mã/tên, lọc hoạt động, phân trang; mã bất biến, chặn mã trùng; giữ chức vụ hệ thống TRUONG_PHONG; chặn ngừng dùng khi còn phân công hiện tại/tương lai |
| Điều chuyển | Khóa giao dịch; kiểm tra trạng thái nhân viên và danh mục; đóng phân công cũ, tạo phân công mới liên tục; xem lịch sử cá nhân/toàn bộ có phân trang; ghi người thực hiện và audit |
| Audit | Tạo nhân viên, PUT hồ sơ, PATCH liên hệ, nghỉ việc, điều chuyển, tạo/sửa/ngừng dùng danh mục và trưởng phòng; đọc theo đối tượng, phân trang; cùng transaction với thay đổi nghiệp vụ |
| Quyền | Các API G1 yêu cầu Principal hợp lệ của ADMIN/HR và quyền EMPLOYEE_MANAGE; kiểm tra tài khoản hoạt động và nhân viên còn làm việc; EMPLOYEE bị từ chối |

## 6. Hợp đồng API và giới hạn đã chốt

- Backend bình thường giữ cổng **2005**, server test dùng **2006**. Các đường dẫn nghiệp vụ hiện tại không có tiền tố `/api`; `/api/health` là alias health. Proxy Angular cũ vẫn xử lý rewrite như trước.
- Tạo danh mục trả 201. PUT trả 200. POST nghỉ việc và kết thúc nhiệm kỳ trả 204. DELETE danh mục là ngừng dùng, trả 200. DELETE nhân viên cũ vẫn tồn tại và đi qua cùng nghiệp vụ nghỉ việc có audit.
- PUT hồ sơ yêu cầu `fullName`; các trường tùy chọn của DTO PUT không gửi sẽ thành null theo cách thay thế đang có. Address dùng PATCH liên hệ. Mã nhân viên, ngày vào làm, trạng thái và phân công không sửa qua PUT hồ sơ.
- PATCH liên hệ: bỏ trường hoặc null = giữ; chuỗi rỗng = xóa; giá trị khác = chuẩn hóa và cập nhật. PATCH không đổi dữ liệu không ghi thêm audit.
- Ngày nghiệp vụ theo `Asia/Ho_Chi_Minh`; thời điểm audit/created/updated của các luồng G1 dùng UTC. SQL row_version do database sinh và Hibernate đọc lại sau INSERT/UPDATE.
- Khoảng phân công là `[effectiveFrom,effectiveTo)`: tính ngày bắt đầu, không tính ngày kết thúc.
- G1 hiện từ chối ngày vào làm/điều chuyển trong tương lai. Ngày điều chuyển phải sau ngày bắt đầu phân công đang có. Điều chuyển sang cùng phòng và chức vụ bị từ chối.
- Nghỉ việc trực tiếp áp dụng ngày hiện tại, chuyển DA_NGHI ngay, lưu ngày cuối làm và đóng phân công ở ngày kế tiếp. Đây chưa phải duyệt đơn nghỉ việc hoặc lập lịch nghỉ việc tương lai. Phân công vẫn có hiệu lực về ngày trong ngày làm cuối, nhưng tài khoản của người DA_NGHI bị kiểm tra quyền từ chối ngay.
- Ngừng dùng phòng/chức vụ không xóa lịch sử. Nếu còn phân công hiện tại hoặc tương lai, phải xử lý trước. Sau nghỉ việc trong ngày, phân công còn tới ngày kế tiếp nên danh mục vẫn có thể bị chặn trong ngày đó.
- Trường address cũ trong DepartmentRequestDTO được giữ tương thích nhưng không lưu; địa chỉ thuộc hồ sơ nhân viên.
- Dữ liệu audit có thông tin cá nhân nên API audit cũng yêu cầu quyền quản trị nhân sự.

## 7. Tích hợp với nhóm

G1 đã có kiểm tra quyền theo Principal và kiểm tra quyền thực tế trong database. **JWT/đăng nhập thật chưa được triển khai trong repo hiện tại**; đây là phần tích hợp của Việt. Token test cổng 2006 không dùng cho cổng 2005. Khi Việt nối JWT, chỉ cần giữ cùng hợp đồng Principal username và dữ liệu accounts/roles/role_permissions; chạy lại collection bằng token thật và baseUrl cổng 2005. Không bật cơ chế test trong ứng dụng thật.

Frontend Angular chưa được hoàn thiện trong lượt G1 backend này. Các trang placeholder cũ không được đánh dấu là giao diện đã nghiệm thu. Bộ bàn giao này nghiệm thu REST API/backend đúng phạm vi Xuân Anh đang làm.

## 8. Database trên máy khác

Máy hiện tại đã có hrm_test và migration trưởng phòng, không chạy lại script tạo bảng. Máy mới xem `HRM_System/database/README_G1.md`. Không đóng gói application-local.properties, mật khẩu SQL hoặc file runtime token vào Git/ZIP.

Build jar bằng `mvn package` đã được kiểm tra. Jar loại trừ cấu hình datasource local và mã server Postman. Khi chạy jar trên máy khác, cung cấp cấu hình database bên ngoài bằng biến môi trường hoặc Spring config riêng. Source local/test vẫn đọc application-local.properties như trước. Không chia sẻ bản HRM_System.zip cũ đang có trong workspace mà chưa kiểm tra lại nội dung cấu hình.

Các thay đổi đang để trong workspace, chưa commit hoặc push. Xem `G1_DANH_SACH_THAY_DOI.md` để phân biệt lượt triển khai G1 với các thay đổi cũ của bạn.
