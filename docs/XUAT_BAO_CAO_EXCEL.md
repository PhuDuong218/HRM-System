# Xuất báo cáo Excel theo quyền

## Phạm vi

Xuất bốn báo cáo tổng hợp đã có thành file `.xlsx` thực bằng Apache POI. Mỗi API xuất gọi đúng service báo cáo JSON tương ứng rồi ghi các trường trong DTO thành bảng Excel. Không tự lấy thêm danh sách nhân viên, dòng lương cá nhân, tài khoản, mật khẩu hoặc JSON snapshot.

| Báo cáo | API GET xuất file | Quyền |
| --- | --- | --- |
| Nhân sự | `/api/reports/employees/export` | ADMIN/HR + `REPORT_VIEW` |
| Hợp đồng | `/api/reports/contracts/export` | ADMIN/HR + `REPORT_VIEW` |
| Công/nghỉ/tăng ca | `/api/reports/work/export` | ADMIN/HR + `REPORT_VIEW` |
| Quỹ lương | `/api/reports/payroll/export` | ADMIN/HR + `REPORT_VIEW` + `PAYROLL_VIEW` |

Các quyền trên phải có đồng thời, kiểm tra ở controller và service. ADMIN không bỏ qua permission; EMPLOYEE không được tải báo cáo quản trị dù vô tình được gán permission báo cáo. `PAYROLL_MANAGE` và `PAYSLIP_SELF_READ` không thay thế `PAYROLL_VIEW`.

Quyền hiện tại cho phép báo cáo quản trị toàn trung tâm đối với ADMIN/HR đủ quyền. Bộ lọc không thay thế kiểm tra quyền. Không tự diễn giải phạm vi HR phân lịch thành phạm vi báo cáo lương.

## Bộ lọc nhất quán với JSON

| Báo cáo | Tham số |
| --- | --- |
| Nhân sự | Không có bộ lọc trong API hiện tại. |
| Hợp đồng | Bắt buộc `fromDate`, `toDate`; tùy chọn `reportDate` (mặc định ngày hiện tại), `expiringWithinDays` (mặc định 30), `departmentId` (mặc định tất cả). |
| Công/nghỉ/tăng ca | Bắt buộc `fromDate`, `toDate`. |
| Quỹ lương | Bắt buộc `fromDate`, `toDate`. |

Ngày có định dạng `yyyy-MM-dd`. File có sheet **Bo loc**, ghi rõ tham số thực tế đã áp dụng, bao gồm giá trị mặc định đã xác định. Khi đối chiếu hai request hợp đồng qua thời điểm đổi ngày, nên truyền rõ cùng `reportDate`.

Cùng bộ lọc và dữ liệu nguồn không đổi thì số liệu file và JSON giống nhau. Hai request độc lập có thể thấy dữ liệu khác nếu DB thay đổi giữa hai lần gọi; chưa triển khai snapshot dùng chung cho nhiều request.

Các quy tắc nghiệp vụ được giữ nguyên từ service báo cáo:

- Nhân sự theo phòng ban/chức vụ dùng phân công hiệu lực tại ngày chạy báo cáo: `effective_from <= ngày xem` và `effective_to IS NULL OR effective_to > ngày xem`. Phân công bắt đầu trong tương lai không được coi là hiện tại; phân công có ngày kết thúc trong tương lai vẫn được tính. Nếu dữ liệu cũ chồng lấn, chọn ngày bắt đầu gần nhất rồi ID phân công lớn nhất. Người chưa có phân công hiệu lực nằm trong nhóm `CHUA_PHAN_CONG`, `groupId = null`.
- Hợp đồng lọc `DA_KY` và khoảng ngày như API đang có. Không chọn `departmentId` thì không phụ thuộc phân công của nhân viên. Chọn phòng ban thì phân công, kỳ lọc và thời gian hợp đồng phải có phần thời gian chung; dùng `EXISTS` để mỗi hợp đồng chỉ được đếm một lần, kể cả nhân viên có nhiều phân công phù hợp. `reportDate` vẫn là ngày đánh giá hiệu lực/sắp hết hạn hợp đồng, không phải ngày duy nhất để lọc phòng ban. Một hợp đồng có thể thuộc cả hai kết quả lọc phòng ban nếu nhân viên chuyển phòng trong kỳ; không cộng các kết quả đó để suy ra số hợp đồng duy nhất toàn trung tâm.
- Công chỉ lấy bản ghi đã xác nhận; nghỉ chỉ lấy đơn đã duyệt; tăng ca phải được duyệt và xác nhận thời gian thực tế. Giữ cách giới hạn thời lượng trong kỳ và phân bổ phút nghỉ theo tỷ lệ. Công/vắng đếm bản ghi, không tự quy đổi thành ngày công 8 giờ.
- Lương chọn nguyên kỳ nằm trọn trong khoảng lọc; mỗi kỳ chỉ lấy phiên bản `DA_CHOT` mới nhất, không lấy `NHAP`. Bản nháp mới hơn không che bản chốt cũ. Phụ cấp/thưởng đã nằm trong tổng trước khấu trừ; thực nhận bằng tổng ADD trừ tổng DEDUCT.
- Không có dữ liệu vẫn tạo file có tiêu đề, bộ lọc, bảng rỗng hoặc các tổng 0 theo DTO, thay vì tự mở rộng bộ lọc.

## Nội dung file

| File | Các sheet |
| --- | --- |
| `employees-report.xlsx` | `Bo loc`, `Theo phong ban`, `Theo chuc vu`, `Theo trang thai`, `Theo trinh do` |
| `contracts-report.xlsx` | `Bo loc`, `Chi tieu` |
| `work-report.xlsx` | `Bo loc`, `Chi tieu` |
| `payroll-report.xlsx` | `Bo loc`, `Tong hop`, `Theo batch` |

File lương chỉ có tổng và thông tin kỳ/batch, không có ID/họ tên hoặc lương từng nhân viên. `periodEndExclusive` là ngày cuối kỳ không bao gồm. ID nhóm, kỳ và batch được lưu dạng chữ để tránh Excel đổi mã. Ngày được lưu dạng ISO. Tiền/giờ hiển thị 4 chữ số thập phân; giá trị vượt 15 chữ số có nghĩa được lưu dạng chữ để giữ chính xác. Chuỗi được ghi thành ô văn bản, không được diễn giải thành công thức hoặc liên kết ngoài.

Response thành công có:

```text
Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
Content-Disposition: attachment; filename="...-report.xlsx"
Cache-Control: no-store
X-Content-Type-Options: nosniff
```

File được tạo trong bộ nhớ và trả trực tiếp cho request đã xác thực. Không lưu file vào thư mục public hoặc phát URL tải không cần quyền. Phạm vi hiện tại là báo cáo tổng hợp, chưa có hàng đợi hoặc lịch sử tải lại file đã sinh.

## Audit và các bảng CSDL

Xác thực/phân quyền dùng `accounts`, `roles`, `permissions`, `role_permissions` qua cơ chế JWT hiện có. Không xuất các bảng này ra Excel. Dữ liệu báo cáo vẫn lấy từ các bảng nguồn qua service báo cáo:

- Nhân sự: `employees`, `employee_assignments`, `departments`, `positions`, `employee_qualifications`, `qualification_levels`.
- Hợp đồng: `contracts`, `employee_assignments`.
- Công/nghỉ/tăng ca: `attendance_records`, `employee_requests`, `leave_request_details`, `overtime_requests`.
- Lương: `payroll_periods`, `payroll_batches`, `payroll_details`, `payroll_lines`.

Sau khi tạo thành công bytes Excel, service ghi thêm `audit_logs` trong transaction có quyền ghi:

| Cột | Giá trị |
| --- | --- |
| `actor_account_id` | ID tài khoản lấy từ principal đã xác thực, không nhận từ client. |
| `action` | `REPORT_EXPORT_GENERATED` |
| `target_table` | `employees`, `contracts`, `attendance_records` hoặc `payroll_batches` |
| `target_record_key` | `EMPLOYEES`, `CONTRACTS`, `WORK` hoặc `PAYROLL`; đây là mã báo cáo tổng hợp, không phải ID một dòng dữ liệu. |
| `reason` | Định dạng XLSX và bộ lọc thực tế; với lương thêm cách chọn bản chốt mới nhất. |
| `correlation_id` | UUID cho lần tạo file. |
| `occurred_at` | Giá trị UTC mặc định của DB. |

Không ghi token, mật khẩu, số tiền hay nội dung file vào audit. Không sửa schema. Nếu tạo file hoặc ghi/commit audit thất bại, API không trả file. Action chỉ xác nhận **đã tạo file**, không chứng minh người dùng đã tải/mở file thành công; mất kết nối sau commit vẫn có thể để lại audit. Các request bị từ chối quyền hoặc bộ lọc lỗi không tạo audit xuất thành công.

## Sử dụng và đối chiếu bằng Postman

1. Khởi động lại backend. Đăng nhập để lấy `accessToken` của tài khoản phù hợp.
2. Tạo GET, Authorization chọn Bearer Token và dán riêng giá trị token. Không gửi body; giữ Accept mặc định `*/*` để nhận được JSON lỗi nếu request không hợp lệ.
3. Dùng một trong các URL:

   ```text
   http://localhost:2005/api/reports/employees/export
   http://localhost:2005/api/reports/contracts/export?fromDate=2026-01-01&toDate=2026-12-31&reportDate=2026-10-25&expiringWithinDays=30
   http://localhost:2005/api/reports/work/export?fromDate=2026-09-01&toDate=2026-09-30
   http://localhost:2005/api/reports/payroll/export?fromDate=2026-08-01&toDate=2026-08-31
   ```

4. Lưu response thành file `.xlsx` rồi mở bằng Excel. Có thể dùng chức năng tải response của Postman. Kiểm tra sheet `Bo loc` và số liệu; gọi lại URL bỏ `/export` với cùng query để đối chiếu JSON.
5. Thử tài khoản thiếu `REPORT_VIEW`: không được nhận file. Thử báo cáo lương với tài khoản có `REPORT_VIEW` nhưng thiếu `PAYROLL_VIEW`: kỳ vọng 403. Thử EMPLOYEE: kỳ vọng 403.
6. Đối chiếu một kỳ chỉ có NHAP: Excel lương phải có tổng 0 và không chứa batch nháp. Kỳ có nhiều bản chốt chỉ xuất bản chốt mới nhất.
7. Kiểm tra audit bằng truy vấn chỉ đọc:

   ```sql
   USE hrm_db;

   SELECT TOP (20)
       audit_id, actor_account_id, occurred_at, action,
       target_table, target_record_key, reason, correlation_id
   FROM dbo.audit_logs
   WHERE action = 'REPORT_EXPORT_GENERATED'
   ORDER BY audit_id DESC;
   ```

Các bước trên là hướng dẫn nghiệm thu, chưa phải kết quả kiểm thử đã chạy. Tải file lương chỉ thành công khi có quyền thật trong DB đang kết nối. Backend không tự cấp thêm permission hoặc tự chốt batch để phục vụ xuất file.

## Thư viện

Sử dụng `org.apache.poi:poi-ooxml:6.0.0`; thông tin bản phát hành và API tạo workbook tham khảo [Apache POI downloads](https://poi.apache.org/download.cgi) và [hướng dẫn XSSF](https://poi.apache.org/components/spreadsheet/quick-guide.html).
