# Chỉ đề xuất — CHƯA ÁP

- QA-06-QA-07.diff: mã kỳ trùng409, khoảng ngày không hợp lệ400.
- QA-08-QA-09.diff: chốt trước tính/công bố trước chốt409.
- QA-10.diff: đưa nhãn/trạng thái/fallback/câu xác nhận của trang Payroll qua bản dịch. Thông điệp động do server trả vẫn cần từ điển/lược đồ mã lỗi để dịch đủ. Cần kiểm tra những nhãn nằm trong modal/placeholder sau áp.
- QA-11.diff: thêm employeeCode/employeeName từ EmployeeRepository sau lọc scope; lấy hàng loạt để tránh N+1, không đưa CCCD/địa chỉ vào response. Angular model hiện đã có hai trường optional. Constructor cũ được giữ nhưng test khởi tạo controller thủ công cần cung cấp repository qua Spring/test injection khi kiểm tra đường dương tính.
- unique-index-qualification.sql: thiết kế staged migration, canonical NFC từ Java và khóa có điều kiện. Chưa backfill, chưa chạy, chưa giải quyết bản trùng cũ. Không tự xóa QA6/DEMO.

Các diff sinh từ source hiện tại, **chưa compile/chạy integration**. QA-10 và QA-11 cùng sửa payroll.html; khi được phép phải tích hợp hai patch bằng review, không áp mù. Không tự commit/push.

Nếu muốn áp sau này: backup DB; đối chiếu diff với source mới; thêm test400/409, scope HR và mã/tên trả về, VI/EN sauF5; build và kiểm tra responsive. Không đổi công thức lương.
