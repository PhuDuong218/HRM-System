# Database HRM cho trung tâm giáo dục

Bản bàn giao dùng `hrm_center_education_v4`. Bộ dựng lấy schema và quy tắc SQL Server trong repo, sau đó nạp dữ liệu giả lập. Chạy trên cùng SQL Server với backend. Mỗi lần dựng cần một tên database chưa tồn tại.

## Bắt đầu

1. Nếu database đã được dựng, chọn nó trong cấu hình chạy backend và mở tệp tài khoản riêng ở `HRM_System/target/education-center/hrm_center_education_v4/TAI_KHOAN.txt`. Sao lưu tệp tài khoản ngoài repo trước khi chạy Maven clean.
2. Nếu dựng trên máy khác, chạy lệnh dưới đây từ thư mục gốc repo. Công cụ dùng Node.js, Java và các thư viện BCrypt đã tải trong Maven cache. Có thể truyền tên mới như `hrm_center_education_thu` làm đối số cuối.

   ```bat
   node HRM_System\database\deployments\education-center\prepare.cjs hrm_center_education_v4
   ```

3. Mở **toàn bộ** tệp `DUNG_DATABASE.sql` do công cụ sinh trong SSMS, kết nối SQL Server rồi nhấn **Execute**. Tệp chạy được ở chế độ truy vấn thông thường. Chờ dòng `COMMITTED`. Nếu có lỗi sau bước tạo database, giao dịch cài schema và nạp dữ liệu sẽ rollback. Dùng tên mới cho lần thử tiếp theo.
4. Dừng backend cũ và chạy lại bằng CMD trong thư mục `HRM_System`. Giữ thông tin đăng nhập SQL Server trong cấu hình local và dùng khóa JWT của môi trường đang chạy. Thay `localhost,1433` bằng máy SQL Server đã chạy script khi cần.

   ```bat
   set "HRM_DB_URL=jdbc:sqlserver://localhost:1433;databaseName=hrm_center_education_v4;encrypt=false;trustServerCertificate=true"
   .\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local,hrm-db
   ```

5. Đăng xuất khỏi phiên cũ trên web và đăng nhập bằng tài khoản mới. Frontend tiếp tục gọi cổng backend đã cấu hình.

## Kịch bản có sẵn

| Kịch bản | Dữ liệu |
| --- | --- |
| Quản trị | `quantri` là ADMIN duy nhất và có phân công Quản trị hệ thống tại Công nghệ thông tin. |
| Phạm vi HR | `nhansu.thanhha` phụ trách Đào tạo và Tư vấn tuyển sinh. `nhansu.minhchau` phụ trách Hành chính nhân sự, Kế toán và Công nghệ thông tin. |
| Phân công | NV008 thuộc Kế toán, giữ chức vụ Kế toán tổng hợp. NV009 có lịch sử chuyển từ Chăm sóc học viên sang Giáo viên. |
| Duyệt hai cấp | Mở `NP-NV009` bằng `truongphong.daotao` để duyệt cấp 1, rồi dùng `nhansu.thanhha` để duyệt cuối. Có lịch ca và quỹ phép theo năm để đối chiếu. |
| Thôi việc | `NVIEC-NV011` đang chờ. Trưởng phòng Kế toán kiểm tra cấp 1, rồi HR Minh Châu xử lý. Hợp đồng còn hiệu lực giúp thử bước kiểm tra bàn giao. |
| Nghỉ việc theo ngày hiệu lực | NV033 đã có quyết định và vẫn đang làm trước ngày cuối. NV034 đã nghỉ, tài khoản đã khóa, phân công và hợp đồng đã kết thúc. |
| Bằng cấp và hợp đồng | NV009 có chứng chỉ gần hết hạn, NV012 có chứng chỉ đã hết hạn, NV031 có hợp đồng còn khoảng 20 ngày. NV032 đang làm nhưng hợp đồng đã hết hạn. |
| Chấm công và tăng ca | NV008 vào muộn 22 phút. NV016 về sớm 35 phút. `OT-NV012` đang chờ duyệt sau ca làm. |
| Lương | Có kỳ tháng trước với công đã xác nhận và kỳ hiện tại còn ca chưa hoàn tất. Cửa sổ ngày 3–5 tháng sau vẫn áp dụng. Để test nhánh thành công ngoài cửa sổ, dùng đồng hồ giả lập trong môi trường test. |

Khóa ngoại `FK_assignment_department_position` yêu cầu cặp phòng ban và chức vụ tồn tại trong `dbo.department_positions`. Mỗi phòng mẫu có một ánh xạ `TRUONG_PHONG`. Khi tạo phòng hoặc chức vụ mới, quản trị DB cần bổ sung các cặp hợp lệ trước khi phân công. Danh sách chọn trên web hiện lấy chức vụ từ danh mục chung.

```sql
-- Ví dụ gắn một chức vụ mới của Kế toán sau khi tạo chức vụ trên web.
USE hrm_center_education_v4;
INSERT dbo.department_positions(department_id, position_id)
SELECT d.department_id, p.position_id
FROM dbo.departments d CROSS JOIN dbo.positions p
WHERE d.department_code = 'KT' AND p.position_code = 'KT_CONG_NO';
```

Sau khi kiểm tra backend đã chạy với v4 và sao lưu database mẫu v3 trong SSMS, có thể xóa riêng v3 bằng các lệnh sau. Dừng các ứng dụng còn kết nối với v3 trước khi chạy.

```sql
USE master;
GO
DROP DATABASE IF EXISTS [hrm_center_education_v3];
```

Các tệp sinh ra trong `target/education-center` chứa hash hoặc mật khẩu mẫu. Giữ chúng trên máy phục vụ demo.
