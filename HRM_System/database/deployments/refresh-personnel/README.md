# Bộ dữ liệu vận hành giả lập

Đã thay dữ liệu của `hrm_center_full` ngày 06/11/2026. Đây là dữ liệu giả lập sát nghiệp vụ trung tâm giáo dục, không phải hồ sơ người thật. Tên trường học được dùng làm bối cảnh giả lập; CCCD, liên hệ và bằng cấp không xác nhận bất kỳ cá nhân thật nào.

- 32 hồ sơ NV001–NV032, 32 tài khoản; ADMIN duy nhất `quantri`.
- 5 phòng: Hành chính nhân sự, Đào tạo, Tư vấn tuyển sinh, Kế toán, Công nghệ thông tin.
- 7 chức vụ; mỗi người có phân công hiện hành, hợp đồng, điều khoản lương, bằng cấp chính và quỹ phép năm.
- HR `nhansu.thanhha`: Đào tạo và Tư vấn tuyển sinh. HR `nhansu.minhchau`: Hành chính nhân sự, Kế toán và Công nghệ thông tin.
- Trưởng phòng dùng tài khoản riêng, quyền duyệt cấp 1 dựa vào nhiệm kỳ, không được cấp role ADMIN.
- Có lịch, công quá khứ, đơn chờ duyệt, lịch sử điều chuyển, tăng ca chờ duyệt. Lương chỉ là nháp chưa tính, chưa chốt/công bố.

Mật khẩu ngẫu nhiên riêng từng tài khoản nằm ngoài repo tại `%LOCALAPPDATA%\HRM-Center\TAI_KHOAN_MOI.txt`. Biên nhận đường dẫn backup trước khi thay nằm cạnh đó trong `BACKUP_PATH.txt`. Không commit hay chia sẻ tệp mật khẩu/khóa JWT. Thư mục `target/data-refresh-private` cũng có bản sao, nhưng sẽ mất khi chạy Maven clean.

Chạy backend từ thư mục gốc repo trong CMD:

```bat
powershell -NoProfile -ExecutionPolicy Bypass -File HRM_System\database\deployments\refresh-personnel\start-backend.ps1
```

Script sử dụng khóa JWT lưu riêng trên máy, không dựng lại dữ liệu. Frontend chạy theo scripts của `frontend/package.json`.

## Bổ sung loại nghỉ

`node HRM_System/database/deployments/refresh-personnel/add-leave-types.cjs` thêm loại còn thiếu qua API có audit, không reset DB. Đã bổ sung `THAI_SAN` (yêu cầu minh chứng, cho nộp bù tối đa 7 ngày) và `VIEC_RIENG` (không lương, không cho nộp bù). Hai loại không trừ quỹ phép năm; loại `OM_DAU` hiện có cũng không trừ quỹ và yêu cầu minh chứng. `isPaid=false` chỉ là cấu hình không trả lương từ công ty trong bộ dữ liệu này, không tính hay phủ nhận quyền hưởng chế độ bảo hiểm. Không tự tính trợ cấp thai sản trong bản cập nhật này.

Lịch công chỉ lấy đơn nghỉ đã duyệt, chưa hủy; nghỉ ốm có màu/bộ lọc riêng, không còn bị loại khỏi lịch. Đã kiểm tra luồng nghỉ ốm nửa ngày của NV012: thiếu minh chứng trả 400, đủ minh chứng tạo đơn, trưởng phòng duyệt cấp 1, ADMIN duyệt cuối, quỹ phép năm không đổi; hủy xong không còn trong nguồn đơn đã duyệt của lịch. Đơn kiểm tra đã được hủy, audit được giữ nguyên.

## Công cụ dựng lại (không chạy thường ngày)

`prepare.cjs` tạo lại mật khẩu và SQL **xóa toàn bộ dữ liệu**; không chạy lại sau khi bàn giao nếu không định reset DB. SQL tạo ra yêu cầu backup gần đây, dùng một giao dịch, khôi phục và kiểm tra constraints/triggers trước commit. Không đổi cấu trúc, không reseed identity. `backup.sql` tạo backup COPY_ONLY và VERIFYONLY; `schema-fingerprint.sql` đối chiếu cấu trúc trước/sau. Các script phụ thuộc mẫu consolidated hiện có, Java và các jar BCrypt/commons-logging trong Maven cache trên máy dựng.
