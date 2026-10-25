# Mô hình vai trò và ma trận quyền HRM-Center

## 1. Mô hình ba vai trò

| Vai trò | Kênh giao diện theo đặc tả | Trách nhiệm đề xuất | Giới hạn |
| --- | --- | --- | --- |
| ADMIN | Web | Quản trị tài khoản/vai trò/quyền; được gán các quyền nghiệp vụ đã được nhóm thống nhất | Không tự động bỏ qua permission, quyền sở hữu, trạng thái, audit hoặc các điều kiện duyệt/chốt |
| HR | Web | Quản lý nghiệp vụ nhân sự trong phạm vi được cấp; đề xuất v1 là toàn trung tâm | Không quản trị tài khoản/vai trò/quyền; không được tự nâng quyền |
| EMPLOYEE | Android | Xem dữ liệu cá nhân và tạo đơn/tăng ca của mình | Không vào API quản trị, liệt kê nhân viên khác, báo cáo toàn trung tâm hoặc quản trị lương |

Một tài khoản có một role chính qua `accounts.role_id`; `accounts.employee_id` liên kết duy nhất với nhân viên. Role nhận các permission qua bảng `role_permissions`, không cấp quyền trực tiếp theo username trong mô hình hiện tại.

ADMIN và HR cũng có quyền cá nhân. Khi dùng API cá nhân, họ chỉ xem bản thân; việc xem nhân viên khác phải dùng API quản trị và quyền quản trị tương ứng. Việc phân chia Web/Android không thay thế kiểm soát backend; login API dùng chung có thể xác thực cả ba vai trò.

## 2. Quy tắc cấp và kiểm tra quyền đề xuất

1. Mặc định từ chối chức năng chưa có quyền trong danh mục được nhóm thống nhất.
2. ADMIN không phải wildcard. Gán rõ từng mã quyền qua `role_permissions`; không tự cấp mọi permission mới được thêm vào bảng.
3. Các chức năng quản trị tài khoản chỉ cho ADMIN và phải có permission tương ứng. API quản lý nghiệp vụ chỉ cho ADMIN/HR có permission tương ứng. EMPLOYEE không được vượt giới hạn role do gán nhầm một quyền quản lý.
4. API cá nhân lấy employee_id từ tài khoản đã xác thực; không tin employeeId trong body/header/query. Truy vấn chi tiết, danh sách và file đều phải ràng buộc chủ sở hữu.
5. Đề xuất cấm tự duyệt/từ chối đơn và tự duyệt/xác nhận tăng ca của mình cho cả ba role. Người xử lý phải là tài khoản có quyền khác; nếu không có thì chuyển người xử lý, không tự bỏ qua. Phần mở rộng cho HR/ADMIN cần chốt tại D03.
6. Không hard-delete hồ sơ/lịch sử hoặc sửa trực tiếp lương đã chốt. Quyền điều chỉnh lương chỉ tạo phiên bản mới có lý do và audit.
7. `view`, `create`, `update`, `delete`, `approve`, `confirm`, `lock`, `publish` và `adjust` là những quyền riêng. Có quyền xem không đồng nghĩa có quyền sửa.
8. Mã kết thúc bằng `.delete` giữ tương thích ba nhóm controller đang có; nghĩa nghiệp vụ là ngừng sử dụng/chuyển trạng thái thích hợp và giữ lịch sử. Không suy ra quyền chạy DELETE SQL trực tiếp.
9. Tài khoản bị khóa hoặc nhân viên đã nghỉ việc không tiếp tục dùng chức năng dành cho nhân viên, kể cả token còn hạn. Phải kiểm tra lại trạng thái và token_version ở backend.
10. JWT dùng để xác định tài khoản; quyền hiện hành được nạp lại từ DB mỗi request. Thay đổi role/quyền có hiệu lực ở request tiếp theo, không dựa vào role do client tự gửi.
11. Đề xuất phản hồi thống nhất: chưa xác thực/token không hợp lệ/hết hạn là 401; có danh tính nhưng thiếu role/quyền là 403; tài nguyên nằm ngoài scope cá nhân là 404 theo phương án D06.
12. Không trả password_hash/token của người khác; ghi audit cho thay đổi tài khoản/quyền, duyệt, sửa công, chốt và điều chỉnh lương.

## 3. Ma trận quyền cho 15 chức năng

| STT | Chức năng | ADMIN | HR | EMPLOYEE | Phạm vi và điều kiện |
| --- | --- | --- | --- | --- | --- |
| 01 | Đăng nhập và phân quyền | Đăng nhập, quản lý tài khoản/vai trò/quyền, khóa/mở và reset tài khoản khác. | Đăng nhập, đổi mật khẩu và đăng xuất tài khoản mình; không quản trị tài khoản/quyền. | Đăng nhập, đổi mật khẩu và đăng xuất tài khoản mình. | Thao tác phiên chỉ tác động tài khoản hiện tại. Quản trị quyền chỉ ADMIN. |
| 02 | Nhân viên | Xem, tạo, cập nhật, phân công và chuyển trạng thái nghỉ việc. | Xem, tạo, cập nhật, phân công và chuyển trạng thái nghỉ việc. | Chỉ xem hồ sơ mình; chưa cho tự sửa. | ADMIN/HR: toàn trung tâm theo đề xuất. API cá nhân của mọi role: chỉ bản thân; giữ hồ sơ và lịch sử. |
| 03 | Phòng ban | Xem, tạo, cập nhật và ngừng sử dụng danh mục. | Xem, tạo, cập nhật và ngừng sử dụng danh mục. | Xem phòng ban gắn với hồ sơ mình; không liệt kê danh mục quản trị. | Thông tin phòng ban cá nhân nằm trong hồ sơ; phân công nhân viên thuộc chức năng 02. |
| 04 | Chức vụ | Xem, tạo, cập nhật và ngừng sử dụng danh mục. | Xem, tạo, cập nhật và ngừng sử dụng danh mục. | Xem chức vụ gắn với hồ sơ mình. | Không xóa lịch sử chức vụ; phân công nhân viên thuộc chức năng 02. |
| 05 | Trình độ | Quản lý danh mục trình độ và bằng cấp nhân viên. | Quản lý danh mục trình độ và bằng cấp nhân viên. | Xem trình độ/bằng cấp của mình trong hồ sơ. | Cấp trình độ có thể ngừng sử dụng; bằng cấp gắn nhân viên phải giữ đúng phạm vi và lịch sử. |
| 06 | Hợp đồng | Xem, tạo, cập nhật hợp lệ và gia hạn bằng bản ghi mới. | Xem, tạo, cập nhật hợp lệ và gia hạn bằng bản ghi mới. | Chỉ xem hợp đồng và lịch sử của mình. | Không ghi đè/xóa hợp đồng cũ; sửa điều kiện đã ký phải theo cơ chế được nhóm chốt. |
| 07 | Lịch làm việc | Quản lý ca, phân lịch và điều chỉnh lịch hợp lệ. | Quản lý ca, phân lịch và điều chỉnh lịch hợp lệ. | Chỉ xem lịch được phân công cho mình. | Lịch là kế hoạch; đổi lịch không tự tạo công đã xác nhận. Chưa đề xuất thao tác hủy/xóa lịch khi chưa chốt cách lưu lịch sử. |
| 08 | Chấm công | Xem, ghi nhận, sửa có lý do và xác nhận công. | Xem, ghi nhận, sửa có lý do và xác nhận công. | Chỉ xem công mình; check-in/out chưa nằm trong bản cấp quyền cơ sở. | Thay đổi công phải có audit; xử lý dữ liệu liên quan kỳ lương đã chốt phải giữ kết quả lương lịch sử. |
| 09 | Đơn nghỉ phép/nghỉ việc | Xem, duyệt hoặc từ chối đơn; tự tạo/xem đơn cá nhân. | Xem, duyệt hoặc từ chối đơn; tự tạo/xem đơn cá nhân. | Tạo và theo dõi đơn của mình. | Đề xuất cấm tự duyệt/từ chối đơn mình cho mọi role; chỉ xử lý đơn CHO_DUYET. |
| 10 | Tăng ca | Xem, duyệt/từ chối, xác nhận giờ thực tế; đăng ký cá nhân. | Xem, duyệt/từ chối, xác nhận giờ thực tế; đăng ký cá nhân. | Đăng ký và theo dõi tăng ca của mình. | Đề xuất không tự duyệt hoặc xác nhận tăng ca của mình; chỉ tăng ca được duyệt, có giờ thực tế xác nhận mới tính lương. |
| 11 | Phụ cấp | Quản lý danh mục, chính sách và khoản áp dụng cho nhân viên. | Quản lý danh mục, chính sách và khoản áp dụng cho nhân viên. | Chỉ xem phụ cấp áp dụng cho mình. | Áp dụng đúng khoảng hiệu lực; ngừng sử dụng thay vì phá dữ liệu đã dùng tính lương. |
| 12 | Khen thưởng/kỷ luật | Xem, lập, sửa hợp lệ và phê duyệt quyết định; xem quyết định cá nhân. | Xem, lập, sửa hợp lệ và phê duyệt quyết định; xem quyết định cá nhân. | Chỉ xem quyết định liên quan đến mình. | Quyền phê duyệt và tránh tự phê duyệt quyết định liên quan chính mình là đề xuất cần nhóm chốt; khoản ảnh hưởng lương phải đủ điều kiện. |
| 13 | Tiền lương | Xem quản trị, tạo kỳ/bản nháp, tính, chốt và điều chỉnh bằng phiên bản mới. | Xem quản trị, tạo kỳ/bản nháp, tính, chốt và điều chỉnh theo quyền được nhóm chốt. | Không quản trị/tính/chốt lương; chỉ đọc kết quả cá nhân đã công bố. | Lương đã chốt bất biến. payroll.self.view chỉ đọc dữ liệu bản thân đã công bố, kể cả khi người gọi là ADMIN/HR. |
| 14 | Phiếu lương | Tra cứu quản trị, phát hành và công bố phiếu đủ điều kiện. | Tra cứu nghiệp vụ, phát hành và công bố theo quyền được nhóm chốt. | Chỉ xem phiếu mình đã công bố; chưa mặc định được tải PDF. | API cá nhân luôn ràng buộc chủ sở hữu và điều kiện công bố; người dùng không tự gửi trạng thái công bố. |
| 15 | Báo cáo | Xem báo cáo nhân sự, hợp đồng, công và quỹ lương theo quyền từng báo cáo. | Xem báo cáo nhân sự, hợp đồng, công và quỹ lương theo quyền từng báo cáo. | Không truy cập báo cáo quản trị hoặc file xuất toàn trung tâm. | File xuất nếu triển khai phải kiểm tra cùng quyền/phạm vi như báo cáo; định dạng và quyền xuất cần nhóm chốt. |

Phòng ban, chức vụ và trình độ cá nhân được hiển thị cùng hồ sơ qua `employee.self.view`; không cấp `department.view` hoặc `position.view` cho EMPLOYEE để giải quyết nhu cầu này. Bằng cấp của một nhân viên do người quản lý cập nhật phải kiểm tra quyền liên quan và phạm vi nhân viên.

Ở hàng 13, `payroll.self.view` chỉ là đọc kết quả lương đã công bố để tương thích API cá nhân đang có; không cho EMPLOYEE tính/chốt hoặc xem bảng lương quản trị. Tự sửa hồ sơ, check-in/out, tải PDF, xuất báo cáo và sửa/rút đơn chưa nằm trong bộ cấp quyền cơ sở khi nhóm chưa chốt D05.

## 4. Quy ước mã

| Loại | Quy ước đề xuất | Ví dụ / lưu ý |
| --- | --- | --- |
| Mã role | Ba giá trị cố định, chữ hoa | `ADMIN`, `HR`, `EMPLOYEE`; SQL đã có CHECK giới hạn |
| Mã permission | Chữ thường, các thành phần cách nhau bằng dấu chấm | `department.view`, `employee.create` |
| Phạm vi cá nhân | `<domain>.self.<action>` | `contract.self.view`, `request.self.create` |
| Báo cáo | `<domain>.report.view` | `employee.report.view`, `work.report.view` |
| Tên hiển thị | Tiếng Việt trong permission_name; mã giữ ổn định | Đổi nhãn hiển thị không làm thay đổi mã |
| ID database | Khóa chính/tham chiếu; không dùng giá trị cố định làm quyền | Không giả định role_id = 1 nghĩa là ADMIN |
| Mã nghiệp vụ | employee_code, department_code, request_code… là định danh nghiệp vụ | Không phải permission; quy tắc tự sinh các mã này cần tài liệu nghiệp vụ riêng |

Mẫu này là **đề xuất mới để nhóm chốt**, vì file Word và schema chỉ quy định permission_code bắt buộc, duy nhất, tối đa 100 ký tự; chúng không bắt buộc một kiểu đặt tên. Chọn dạng dấu chấm giúp giữ nguyên 17 mã đã được controller kiểm tra.

Không dùng wildcard hoặc chuỗi mở rộng tự động. Ví dụ `hasAuthority('department.*')` không thay cho việc khai báo từng mã. Cũng không mặc định `employee.view` bao gồm `employee.report.view` hoặc `employee.self.view`.

`account.assign` được định nghĩa riêng là gán role cho tài khoản; `role.assign` là gán permission cho role. Hai quyền này chỉ dành cho ADMIN. Danh mục mới được bổ sung theo thay đổi có kiểm soát, không tạo role ngoài ba role cố định.

## 5. Danh mục permission đề xuất và role được gán

Có **79 mã đề xuất**, gồm **17 mã đang được controller kiểm tra** và **62 mã bổ sung cho thiết kế đầy đủ**. Cột “Hiện trạng” nói về việc mã xuất hiện trong kiểm tra code, không có nghĩa mã đã có trong DB, đã được gán cho role hoặc endpoint đã chạy thành công.

Các role trong bảng là phân bổ đề xuất, chưa phải quyền đã được cấp. ADMIN/HR là hai role được gán tường minh, không phải quan hệ kế thừa. Những mã duyệt/xác nhận/chốt/công bố/điều chỉnh vẫn chờ D03–D04.

| Module | permission_code | Thao tác | Role đề xuất | Phạm vi | Hiện trạng |
| --- | --- | --- | --- | --- | --- |
| 01 | `account.view` | Xem danh sách/chi tiết tài khoản; không trả password_hash | ADMIN | Quản trị tài khoản/quyền; không vượt quy tắc nghiệp vụ | Mã đề xuất; chưa được endpoint hiện tại kiểm tra |
| 01 | `account.create` | Tạo tài khoản liên kết nhân viên hợp lệ | ADMIN | Quản trị tài khoản/quyền; không vượt quy tắc nghiệp vụ | Mã đề xuất; chưa được endpoint hiện tại kiểm tra |
| 01 | `account.update` | Sửa thông tin tài khoản được phép; không bao gồm đổi role, khóa hoặc reset | ADMIN | Quản trị tài khoản/quyền; không vượt quy tắc nghiệp vụ | Mã đề xuất; chưa được endpoint hiện tại kiểm tra |
| 01 | `account.lock` | Khóa/mở tài khoản; chính sách vô hiệu phiên xem quyết định D06 | ADMIN | Quản trị tài khoản/quyền; không vượt quy tắc nghiệp vụ | Mã đề xuất; chưa được endpoint hiện tại kiểm tra |
| 01 | `account.reset` | Reset mật khẩu tài khoản khác; không reset chính mình bằng API quản trị | ADMIN | Quản trị tài khoản/quyền; không vượt quy tắc nghiệp vụ | Mã đề xuất; chưa được endpoint hiện tại kiểm tra |
| 01 | `account.assign` | Gán một trong ba role cố định cho tài khoản | ADMIN | Quản trị tài khoản/quyền; không vượt quy tắc nghiệp vụ | Mã đề xuất; chưa được endpoint hiện tại kiểm tra |
| 01 | `role.view` | Xem ba role cố định | ADMIN | Quản trị tài khoản/quyền; không vượt quy tắc nghiệp vụ | Mã đề xuất; chưa được endpoint hiện tại kiểm tra |
| 01 | `role.assign` | Gán/gỡ các quyền đã được thống nhất cho một role | ADMIN | Quản trị tài khoản/quyền; không vượt quy tắc nghiệp vụ | Mã đề xuất; chưa được endpoint hiện tại kiểm tra |
| 01 | `permission.view` | Xem danh mục quyền; bổ sung mã mới theo thay đổi có kiểm soát | ADMIN | Quản trị tài khoản/quyền; không vượt quy tắc nghiệp vụ | Mã đề xuất; chưa được endpoint hiện tại kiểm tra |
| 02 | `employee.view` | Xem nhân viên trong phạm vi quản lý | ADMIN, HR | Quản lý toàn trung tâm (đề xuất) | Đã được code hiện tại kiểm tra |
| 02 | `employee.create` | Tạo nhân viên | ADMIN, HR | Quản lý toàn trung tâm (đề xuất) | Đã được code hiện tại kiểm tra |
| 02 | `employee.update` | Cập nhật nhân viên và liên kết hợp lệ | ADMIN, HR | Quản lý toàn trung tâm (đề xuất) | Đã được code hiện tại kiểm tra |
| 02 | `employee.delete` | Chuyển nhân viên sang nghỉ việc và xử lý truy cập; giữ hồ sơ | ADMIN, HR | Quản lý toàn trung tâm (đề xuất) | Đã được code hiện tại kiểm tra |
| 03 | `department.view` | Xem phòng ban trong phạm vi quản lý | ADMIN, HR | Quản lý toàn trung tâm (đề xuất) | Đã được code hiện tại kiểm tra |
| 03 | `department.create` | Tạo phòng ban | ADMIN, HR | Quản lý toàn trung tâm (đề xuất) | Đã được code hiện tại kiểm tra |
| 03 | `department.update` | Cập nhật phòng ban và liên kết hợp lệ | ADMIN, HR | Quản lý toàn trung tâm (đề xuất) | Đã được code hiện tại kiểm tra |
| 03 | `department.delete` | Ngừng sử dụng phòng ban; không phá lịch sử/tham chiếu | ADMIN, HR | Quản lý toàn trung tâm (đề xuất) | Đã được code hiện tại kiểm tra |
| 04 | `position.view` | Xem chức vụ trong phạm vi quản lý | ADMIN, HR | Quản lý toàn trung tâm (đề xuất) | Đã được code hiện tại kiểm tra |
| 04 | `position.create` | Tạo chức vụ | ADMIN, HR | Quản lý toàn trung tâm (đề xuất) | Đã được code hiện tại kiểm tra |
| 04 | `position.update` | Cập nhật chức vụ và liên kết hợp lệ | ADMIN, HR | Quản lý toàn trung tâm (đề xuất) | Đã được code hiện tại kiểm tra |
| 04 | `position.delete` | Ngừng sử dụng chức vụ; không phá lịch sử/tham chiếu | ADMIN, HR | Quản lý toàn trung tâm (đề xuất) | Đã được code hiện tại kiểm tra |
| 05 | `qualification.view` | Xem danh mục trình độ/bằng cấp trong phạm vi quản lý | ADMIN, HR | Quản lý toàn trung tâm (đề xuất) | Mã đề xuất; chưa được endpoint hiện tại kiểm tra |
| 05 | `qualification.create` | Tạo danh mục trình độ/bằng cấp | ADMIN, HR | Quản lý toàn trung tâm (đề xuất) | Mã đề xuất; chưa được endpoint hiện tại kiểm tra |
| 05 | `qualification.update` | Cập nhật danh mục trình độ/bằng cấp và liên kết hợp lệ | ADMIN, HR | Quản lý toàn trung tâm (đề xuất) | Mã đề xuất; chưa được endpoint hiện tại kiểm tra |
| 05 | `qualification.delete` | Ngừng dùng cấp trình độ; không xóa bằng cấp/lịch sử nhân viên | ADMIN, HR | Quản lý toàn trung tâm (đề xuất) | Mã đề xuất; chưa được endpoint hiện tại kiểm tra |
| 02 | `employee.self.view` | Xem hồ sơ mình, gồm phòng ban/chức vụ/trình độ được phép hiển thị | ADMIN, HR, EMPLOYEE | Chỉ employee_id của tài khoản hiện tại | Mã đề xuất; chưa được endpoint hiện tại kiểm tra |
| 06 | `contract.view` | Xem hợp đồng trong phạm vi quản lý | ADMIN, HR | Quản lý toàn trung tâm (đề xuất) | Mã đề xuất; chưa được endpoint hiện tại kiểm tra |
| 06 | `contract.create` | Tạo hợp đồng mới | ADMIN, HR | Quản lý toàn trung tâm (đề xuất) | Mã đề xuất; chưa được endpoint hiện tại kiểm tra |
| 06 | `contract.update` | Cập nhật hợp lệ, giữ căn cứ/lịch sử | ADMIN, HR | Quản lý toàn trung tâm (đề xuất) | Mã đề xuất; chưa được endpoint hiện tại kiểm tra |
| 06 | `contract.renew` | Gia hạn/ký mới bằng bản ghi mới; giữ bản cũ | ADMIN, HR | Quản lý toàn trung tâm (đề xuất) | Mã đề xuất; chưa được endpoint hiện tại kiểm tra |
| 06 | `contract.self.view` | Xem hợp đồng và lịch sử của mình | ADMIN, HR, EMPLOYEE | Chỉ employee_id của tài khoản hiện tại | Mã đề xuất; chưa được endpoint hiện tại kiểm tra |
| 07 | `shift.view` | Xem ca làm | ADMIN, HR | Quản lý toàn trung tâm (đề xuất) | Mã đề xuất; chưa được endpoint hiện tại kiểm tra |
| 07 | `shift.create` | Tạo ca làm | ADMIN, HR | Quản lý toàn trung tâm (đề xuất) | Mã đề xuất; chưa được endpoint hiện tại kiểm tra |
| 07 | `shift.update` | Cập nhật ca làm hợp lệ | ADMIN, HR | Quản lý toàn trung tâm (đề xuất) | Mã đề xuất; chưa được endpoint hiện tại kiểm tra |
| 07 | `shift.delete` | Ngừng dùng/hủy ca làm hợp lệ, bảo toàn dữ liệu tham chiếu | ADMIN, HR | Quản lý toàn trung tâm (đề xuất) | Mã đề xuất; chưa được endpoint hiện tại kiểm tra |
| 07 | `schedule.view` | Xem lịch làm việc | ADMIN, HR | Quản lý toàn trung tâm (đề xuất) | Mã đề xuất; chưa được endpoint hiện tại kiểm tra |
| 07 | `schedule.create` | Tạo lịch làm việc | ADMIN, HR | Quản lý toàn trung tâm (đề xuất) | Mã đề xuất; chưa được endpoint hiện tại kiểm tra |
| 07 | `schedule.update` | Cập nhật lịch làm việc hợp lệ | ADMIN, HR | Quản lý toàn trung tâm (đề xuất) | Mã đề xuất; chưa được endpoint hiện tại kiểm tra |
| 07 | `schedule.self.view` | Xem lịch của mình | ADMIN, HR, EMPLOYEE | Chỉ employee_id của tài khoản hiện tại | Mã đề xuất; chưa được endpoint hiện tại kiểm tra |
| 08 | `attendance.view` | Xem công trong phạm vi quản lý | ADMIN, HR | Quản lý toàn trung tâm (đề xuất) | Mã đề xuất; chưa được endpoint hiện tại kiểm tra |
| 08 | `attendance.create` | Ghi nhận công theo nguồn được nhóm chốt | ADMIN, HR | Quản lý toàn trung tâm (đề xuất) | Mã đề xuất; chưa được endpoint hiện tại kiểm tra |
| 08 | `attendance.update` | Sửa công có lý do và audit | ADMIN, HR | Quản lý toàn trung tâm (đề xuất) | Mã đề xuất; chưa được endpoint hiện tại kiểm tra |
| 08 | `attendance.confirm` | Xác nhận công đủ điều kiện; quyền xử lý xem D03 | ADMIN, HR | Quản lý toàn trung tâm (đề xuất) | Mã đề xuất; chưa được endpoint hiện tại kiểm tra |
| 08 | `attendance.self.view` | Xem lịch sử công của mình | ADMIN, HR, EMPLOYEE | Chỉ employee_id của tài khoản hiện tại | Mã đề xuất; chưa được endpoint hiện tại kiểm tra |
| 09 | `request.view` | Xem đơn trong phạm vi quản lý | ADMIN, HR | Quản lý toàn trung tâm (đề xuất) | Mã đề xuất; chưa được endpoint hiện tại kiểm tra |
| 09 | `request.approve` | Duyệt đơn CHO_DUYET; không tự duyệt theo đề xuất D03 | ADMIN, HR | Quản lý toàn trung tâm (đề xuất) | Mã đề xuất; chưa được endpoint hiện tại kiểm tra |
| 09 | `request.reject` | Từ chối đơn CHO_DUYET có lý do; không tự xử lý theo D03 | ADMIN, HR | Quản lý toàn trung tâm (đề xuất) | Mã đề xuất; chưa được endpoint hiện tại kiểm tra |
| 09 | `request.self.create` | Tạo đơn nghỉ phép/nghỉ việc của mình, không tự đặt trạng thái duyệt | ADMIN, HR, EMPLOYEE | Chủ sở hữu lấy từ phiên | Mã đề xuất; chưa được endpoint hiện tại kiểm tra |
| 09 | `request.self.view` | Xem đơn của mình | ADMIN, HR, EMPLOYEE | Chỉ employee_id của tài khoản hiện tại | Mã đề xuất; chưa được endpoint hiện tại kiểm tra |
| 10 | `overtime.view` | Xem tăng ca trong phạm vi quản lý | ADMIN, HR | Quản lý toàn trung tâm (đề xuất) | Mã đề xuất; chưa được endpoint hiện tại kiểm tra |
| 10 | `overtime.approve` | Duyệt tăng ca; không tự duyệt theo D03 | ADMIN, HR | Quản lý toàn trung tâm (đề xuất) | Mã đề xuất; chưa được endpoint hiện tại kiểm tra |
| 10 | `overtime.reject` | Từ chối tăng ca có lý do; không tự xử lý theo D03 | ADMIN, HR | Quản lý toàn trung tâm (đề xuất) | Mã đề xuất; chưa được endpoint hiện tại kiểm tra |
| 10 | `overtime.confirm` | Xác nhận giờ tăng ca thực tế; không tự xác nhận theo D03 | ADMIN, HR | Quản lý toàn trung tâm (đề xuất) | Mã đề xuất; chưa được endpoint hiện tại kiểm tra |
| 10 | `overtime.self.create` | Đăng ký tăng ca của mình | ADMIN, HR, EMPLOYEE | Chủ sở hữu lấy từ phiên | Mã đề xuất; chưa được endpoint hiện tại kiểm tra |
| 10 | `overtime.self.view` | Xem tăng ca của mình | ADMIN, HR, EMPLOYEE | Chỉ employee_id của tài khoản hiện tại | Mã đề xuất; chưa được endpoint hiện tại kiểm tra |
| 11 | `allowance.view` | Xem danh mục và khoản phụ cấp trong phạm vi quản lý | ADMIN, HR | Quản lý toàn trung tâm (đề xuất) | Mã đề xuất; chưa được endpoint hiện tại kiểm tra |
| 11 | `allowance.create` | Tạo chính sách/khoản áp dụng | ADMIN, HR | Quản lý toàn trung tâm (đề xuất) | Mã đề xuất; chưa được endpoint hiện tại kiểm tra |
| 11 | `allowance.update` | Cập nhật hợp lệ theo khoảng hiệu lực | ADMIN, HR | Quản lý toàn trung tâm (đề xuất) | Mã đề xuất; chưa được endpoint hiện tại kiểm tra |
| 11 | `allowance.delete` | Ngừng dùng/kết thúc hiệu lực, giữ lịch sử | ADMIN, HR | Quản lý toàn trung tâm (đề xuất) | Mã đề xuất; chưa được endpoint hiện tại kiểm tra |
| 11 | `allowance.self.view` | Xem phụ cấp áp dụng cho mình | ADMIN, HR, EMPLOYEE | Chỉ employee_id của tài khoản hiện tại | Mã đề xuất; chưa được endpoint hiện tại kiểm tra |
| 12 | `decision.view` | Xem quyết định trong phạm vi quản lý | ADMIN, HR | Quản lý toàn trung tâm (đề xuất) | Mã đề xuất; chưa được endpoint hiện tại kiểm tra |
| 12 | `decision.create` | Lập quyết định khen thưởng/kỷ luật | ADMIN, HR | Quản lý toàn trung tâm (đề xuất) | Mã đề xuất; chưa được endpoint hiện tại kiểm tra |
| 12 | `decision.update` | Sửa quyết định khi còn được phép; giữ lịch sử | ADMIN, HR | Quản lý toàn trung tâm (đề xuất) | Mã đề xuất; chưa được endpoint hiện tại kiểm tra |
| 12 | `decision.approve` | Phê duyệt quyết định theo D03; không tự phê duyệt quyết định của mình | ADMIN, HR | Quản lý toàn trung tâm (đề xuất) | Mã đề xuất; chưa được endpoint hiện tại kiểm tra |
| 12 | `decision.self.view` | Xem quyết định liên quan đến mình | ADMIN, HR, EMPLOYEE | Chỉ employee_id của tài khoản hiện tại | Mã đề xuất; chưa được endpoint hiện tại kiểm tra |
| 13 | `payroll.view` | Xem dữ liệu lương quản trị trong phạm vi được cấp | ADMIN, HR | Quản lý toàn trung tâm (đề xuất) | Mã đề xuất; chưa được endpoint hiện tại kiểm tra |
| 13 | `payroll.create` | Tạo kỳ/phiên bản bảng lương nháp | ADMIN, HR | Quản lý toàn trung tâm (đề xuất) | Mã đề xuất; chưa được endpoint hiện tại kiểm tra |
| 13 | `payroll.calculate` | Tính bản nháp từ đầu vào hợp lệ | ADMIN, HR | Quản lý toàn trung tâm (đề xuất) | Mã đề xuất; chưa được endpoint hiện tại kiểm tra |
| 13 | `payroll.lock` | Chốt bảng lương đủ điều kiện; xem D04 | ADMIN, HR | Quản lý toàn trung tâm (đề xuất) | Mã đề xuất; chưa được endpoint hiện tại kiểm tra |
| 13 | `payroll.adjust` | Tạo phiên bản điều chỉnh có lý do, audit; giữ bản chốt; xem D04 | ADMIN, HR | Quản lý toàn trung tâm (đề xuất) | Mã đề xuất; chưa được endpoint hiện tại kiểm tra |
| 13 | `payroll.self.view` | Xem kết quả lương cá nhân đã công bố | ADMIN, HR, EMPLOYEE | Chủ sở hữu + điều kiện công bố ở backend | Đã được code hiện tại kiểm tra |
| 14 | `payslip.view` | Tra cứu phiếu lương trong phạm vi quản trị | ADMIN, HR | Quản lý toàn trung tâm (đề xuất) | Mã đề xuất; chưa được endpoint hiện tại kiểm tra |
| 14 | `payslip.issue` | Phát hành phiếu từ kết quả đã chốt | ADMIN, HR | Quản lý toàn trung tâm (đề xuất) | Mã đề xuất; chưa được endpoint hiện tại kiểm tra |
| 14 | `payslip.publish` | Công bố phiếu/kết quả đủ điều kiện; xem D04 | ADMIN, HR | Quản lý toàn trung tâm (đề xuất) | Mã đề xuất; chưa được endpoint hiện tại kiểm tra |
| 14 | `payslip.self.view` | Xem phiếu lương cá nhân đã công bố | ADMIN, HR, EMPLOYEE | Chủ sở hữu + điều kiện công bố ở backend | Đã được code hiện tại kiểm tra |
| 15 | `employee.report.view` | Xem báo cáo nhân sự trong phạm vi được cấp | ADMIN, HR | Quản lý toàn trung tâm (đề xuất) | Đã được code hiện tại kiểm tra |
| 15 | `contract.report.view` | Xem báo cáo hợp đồng trong phạm vi được cấp | ADMIN, HR | Quản lý toàn trung tâm (đề xuất) | Đã được code hiện tại kiểm tra |
| 15 | `work.report.view` | Xem báo cáo lịch/công/nghỉ/tăng ca trong phạm vi được cấp | ADMIN, HR | Quản lý toàn trung tâm (đề xuất) | Đã được code hiện tại kiểm tra |
| 15 | `payroll.report.view` | Xem báo cáo quỹ lương trong phạm vi được cấp | ADMIN, HR | Quản lý toàn trung tâm (đề xuất) | Mã đề xuất; chưa được endpoint hiện tại kiểm tra |

Login không yêu cầu token/permission vì dùng để lấy token. Logout, đổi mật khẩu cá nhân và API tài khoản hiện tại theo thiết kế chỉ cần danh tính hợp lệ cùng điều kiện cá nhân; không cần tạo thêm permission cho mỗi thao tác này. API tài khoản hiện tại chưa có trong code được rà soát.

## 7. Đối chiếu bộ mã hiện thấy trong database

**Bảng này mô tả quan hệ nghiệp vụ để nhóm quyết định; không phải alias đang chạy và không phải lệnh cấp quyền.**

| Mã trong ảnh | Cách phân rã có thể thống nhất | Giới hạn cần ghi rõ |
| --- | --- | --- |
| ACCOUNT_MANAGE | account.view/create/update/lock/reset/assign, role.view/assign, permission.view | Danh sách phân rã viết tắt ở đây; phải dùng từng mã đầy đủ ở mục 6. Chỉ ADMIN. API reset hiện chỉ kiểm tra role, chưa kiểm tra ACCOUNT_MANAGE hoặc account.reset |
| EMPLOYEE_MANAGE | employee.view/create/update/delete | Không tự bao gồm phòng ban, chức vụ, trình độ, hợp đồng hoặc dữ liệu lương |
| PAYROLL_MANAGE | payroll.view/create/calculate/lock/adjust | Không tự bao gồm API lương cá nhân hoặc quyền công bố; công bố được đề xuất kiểm soát bằng payslip.publish |
| PAYSLIP_SELF_READ | payslip.self.view | Tương đồng mục đích đọc phiếu cá nhân; chưa có cơ chế chuyển đổi tên |
| REQUEST_APPROVE | request.approve | Không suy ra quyền tạo/xem mọi đơn; không vượt quy tắc cấm tự duyệt |
| SELF_READ | Tách thành employee.self.view, contract.self.view, schedule.self.view, attendance.self.view, request.self.view, overtime.self.view, allowance.self.view, decision.self.view, payroll.self.view, payslip.self.view | Không cấp ngầm tất cả; dữ liệu lương vẫn phải đã công bố |

Không có mã cũ nào đủ căn cứ để mặc định bao gồm `department.view`. Vì vậy tình huống ADMIN nhận 403 là có thể giải thích được bằng code: authority `ROLE_ADMIN` cộng sáu chuỗi cũ không chứa authority `department.view`.

Lộ trình sau khi nhóm chốt: kiểm kê DB thật → duyệt bảng chuyển đổi từng quyền → viết migration dữ liệu có thể chạy lại và ghi audit → đồng bộ kiểm tra backend → kiểm tra ADMIN/HR/EMPLOYEE A/B → cập nhật tài liệu. Không đổi/xóa hàng loạt mã cũ hoặc tạo lại DB từ tài liệu đề xuất này. Nếu nhóm quyết định giữ bộ mã chữ hoa, phải thay lại toàn bộ danh mục/API theo phạm vi đã chốt, không sửa rời từng annotation.

## 6. Đối chiếu endpoint hiện tại và khoảng thiếu

Đây là inventory từ source, không phải kết quả chạy Postman. Có 25 phương thức endpoint, trong đó 20 phương thức đang dùng 17 permission khác nhau.

| Endpoint hiện tại | Kiểm tra quyền hiện tại | Lưu ý |
| --- | --- | --- |
| POST /api/auth/login | Public; username/password và is_active | Chưa kiểm tra trạng thái nghỉ việc của nhân viên |
| POST /api/auth/logout | Đã xác thực | Tăng token_version của tài khoản hiện tại, vô hiệu các token cũ của tài khoản |
| POST /api/auth/change-password | Đã xác thực | Kiểm tra mật khẩu hiện tại, đổi cho chính mình và tăng token_version |
| POST /api/admin/accounts/{accountId}/reset-password | AuthService kiểm tra role ADMIN | Chưa kiểm tra account.reset; không reset chính mình qua API này |
| GET /employees; GET /employees/{id} | employee.view | Danh sách chặn EMPLOYEE trong service; chi tiết chỉ cho EMPLOYEE xem đúng employeeId. Chưa có API employee.self.view riêng |
| POST /employees | employee.create | Chưa có role guard bổ sung trong controller/service |
| PUT /employees/{id} | employee.update | Chưa có role guard bổ sung trong controller/service |
| DELETE /employees/{id} | employee.delete | Chuyển sang DA_NGHI; chưa tự khóa tài khoản tương ứng |
| GET /departments; GET /departments/{id} | department.view | Không có bypass ADMIN; không có API phòng ban cá nhân riêng |
| POST /departments | department.create | Mã quyền chi tiết, không đọc EMPLOYEE_MANAGE |
| PUT /departments/{id} | department.update | Mã quyền chi tiết |
| DELETE /departments/{id} | department.delete | Ngừng sử dụng |
| GET /positions; GET /positions/{id} | position.view | Không có bypass ADMIN |
| POST /positions | position.create | Mã quyền chi tiết |
| PUT /positions/{id} | position.update | Mã quyền chi tiết |
| DELETE /positions/{id} | position.delete | Ngừng sử dụng |
| GET /api/me/salary | payroll.self.view | employeeId lấy từ principal; truy vấn công bố cần đối chiếu trạng thái SQL |
| GET /api/me/payslips | payslip.self.view | employeeId lấy từ principal; truy vấn công bố cần đối chiếu trạng thái SQL |
| GET /api/reports/employees | employee.report.view | Không có role guard bổ sung |
| GET /api/reports/contracts | contract.report.view | Không có role guard bổ sung |
| GET /api/reports/work | work.report.view | Không có role guard bổ sung |
| GET /hello | Public | Chỉ lời chào; không dùng làm bằng chứng token hợp lệ |

Các điểm phải xử lý khi triển khai ma trận:

- Bộ mã trong ảnh chưa khớp các authority controller kiểm tra; repository chỉ tải nguyên chuỗi, không ánh xạ.
- API quản trị cần ranh giới role cùng permission; chỉ gán nhầm quyền quản lý cho EMPLOYEE không được mở chức năng trái mô hình.
- Chưa có handler ở tầng Security để bảo đảm chưa xác thực trả 401, thiếu quyền trả 403 và body lỗi nhất quán.
- Filter kiểm tra account active/token_version nhưng chưa kiểm tra nghỉ việc và liên kết nhân viên hợp lệ cho truy cập cá nhân.
- `PayrollRepository` đã đồng bộ điều kiện “đã công bố”: batch `DA_CHOT`, đủ metadata chốt/công bố và `published_at >= locked_at`; luôn giới hạn employee_id theo principal. Cần nghiệm thu với dữ liệu thực tế để xác nhận luồng công bố và quyền sở hữu.
- `/api/health` được whitelist nhưng chưa tìm thấy controller tương ứng; không dùng nó làm bằng chứng bảo mật.
- Bài test hiện có chỉ contextLoads; chưa có bằng chứng tự động cho ma trận quyền. Tài liệu này không xác nhận Phase 03 đã đạt.


## 7. Những quyết định mang trao đổi với nhóm

| Mã | Nội dung cần chốt | Phương án đề xuất để thảo luận | Trạng thái / ý kiến nhóm |
| --- | --- | --- | --- |
| D01 | Tên mã quyền và xử lý sáu mã cũ | Dùng danh mục chữ thường có dấu chấm ở mục 6; migration từng mapping sau khi chốt, không alias ngầm | Chưa xác nhận |
| D02 | Quyền ADMIN và phạm vi HR | ADMIN nhận các quyền được liệt kê, không bypass; HR quản lý toàn trung tâm trong v1. Nếu giới hạn theo phòng ban phải thiết kế scope ở truy vấn/service | Chưa xác nhận |
| D03 | Ai duyệt đơn/tăng ca, xác nhận công, phê duyệt quyết định | ADMIN/HR có mã tương ứng; cấm tự xử lý đơn/tăng ca của mình và tự phê duyệt quyết định liên quan mình; dùng người có quyền khác | Chưa xác nhận |
| D04 | Ai chốt/công bố/điều chỉnh lương | Đề xuất cấp từng quyền payroll.lock, payslip.publish, payroll.adjust cho ADMIN/HR; nhóm có thể thu hẹp HR. Không sửa trực tiếp bản chốt | Chưa xác nhận |
| D05 | Tự sửa hồ sơ, check-in/out, rút/sửa đơn, PDF và xuất báo cáo | Chưa đưa vào bộ cấp quyền cơ sở; bổ sung mã cụ thể sau khi chốt nghiệp vụ/định dạng | Chưa xác nhận |
| D06 | Chính sách phiên và mã lỗi | Khóa/mở tài khoản không khôi phục JWT cũ: đề xuất tăng token_version khi thay đổi trạng thái khóa; logout/đổi/reset vô hiệu token cũ; 401/403 và 404 ngoài scope nhất quán | Chưa xác nhận |
| D07 | Điều kiện dữ liệu “đã công bố” | API đã dùng DA_CHOT cùng metadata chốt/công bố hợp lệ theo SQL mới | Đã sửa code; chờ nghiệm thu |

Biên bản chốt: người phụ trách __________________; thành viên xác nhận __________________; ngày __________________; phiên bản sau chốt __________________.

Nội dung có thể ghi vào Notion:

> Đã soạn bản đề xuất mô hình ADMIN/HR/EMPLOYEE và ma trận đủ 15 chức năng, kèm danh mục mã quyền, scope dữ liệu và đối chiếu code/DB. Còn chờ nhóm xác nhận D01–D07; chưa áp dụng thay đổi vào backend/database.

## Phụ lục A. Bảng CSDL theo chức năng

Ba bảng trung tâm là roles, permissions và role_permissions; accounts liên kết tài khoản với role, employees liên kết danh tính nhân viên. Phạm vi cá nhân và điều kiện nghiệp vụ vẫn cần backend kiểm tra, không được thay thế chỉ bằng FK.

| Module | Chức năng | Bảng CSDL liên quan |
| --- | --- | --- |
| 01 | Đăng nhập và phân quyền | accounts; roles; permissions; role_permissions; employees; audit_logs |
| 02 | Nhân viên | employees; employee_assignments; employee_qualifications; accounts |
| 03 | Phòng ban | departments; employee_assignments |
| 04 | Chức vụ | positions; employee_assignments |
| 05 | Trình độ | qualification_levels; employee_qualifications; employees |
| 06 | Hợp đồng | contracts; contract_types; contract_salary_terms |
| 07 | Lịch làm việc | work_shifts; work_schedules |
| 08 | Chấm công | attendance_records; work_schedules; audit_logs |
| 09 | Đơn nghỉ phép/nghỉ việc | employee_requests; leave_request_details; resignation_request_details; leave_types; leave_entitlements |
| 10 | Tăng ca | overtime_requests; audit_logs |
| 11 | Phụ cấp | allowance_types; employee_allowances |
| 12 | Khen thưởng/kỷ luật | employee_decisions; audit_logs |
| 13 | Tiền lương | payroll_periods; payroll_batches; payroll_details; payroll_lines; payroll_snapshots; salary_components |
| 14 | Phiếu lương | payslips; payroll_details; payroll_batches |
| 15 | Báo cáo | Các bảng nghiệp vụ tương ứng báo cáo; audit_logs |

## Phụ lục B. Sử dụng bảng CSV

File [ma_tran_quyen_15_chuc_nang.csv](ma_tran_quyen_15_chuc_nang.csv) chứa đúng 15 hàng, với các cột chức năng, quyền của ba role, phạm vi, bảng CSDL và trạng thái. Có thể dùng tính năng nhập CSV của Notion hoặc mở bằng Excel. CSV và mục 4 cùng là bản đề xuất, chưa phải xác nhận của nhóm.
