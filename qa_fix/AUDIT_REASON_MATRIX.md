# Ma trận lý do và audit

| Thao tác | Lý do bắt buộc? | Audit | Xử lý lượt này |
|---|---|---|---|
| PUT hồ sơ | Có, trim1..1000, DTO + transaction wrapper | Một EMPLOYEE_PROFILE_UPDATE cho thay đổi hồ sơ/gender; snapshot trước/sau | Đã sửa source, test |
| PATCH liên hệ | Có, trim1..1000; kiểm cả khi no-op | EMPLOYEE_CONTACT_UPDATE khi dữ liệu thay đổi | Đã sửa cùng gốc |
| Tạo nhân viên | Giữ hợp đồng tạo cũ, không thêm reason bắt buộc | EMPLOYEE_CREATE; gender tạo giữ event hiện có | Chỉ thêm validation email/CCCD/địa chỉ; không đổi chính sách audit tạo |
| Điều chuyển | `reason==null` vẫn đi overload legacy; trắng bị400 | Có, có thể reason null | Chỉ ghi nhận; đề xuất bắt buộc reason trong vòng riêng |
| Thêm/sửa/gỡ bằng | PersonnelCatalogService.write overload không reason | Có nhưng reason có thể null | Chỉ sửa dedup; không mở rộng yêu cầu reason |
| Đặt bằng chính | Không bắt buộc người dùng nhập lý do | Có event thay đổi chính; reason null | Giữ nguyên; đề xuất xác nhận/lý do tùy nghiệp vụ |
| Danh mục trình độ/loại phép | Các command hiện có reason không đồng nhất | Có qua PersonnelCatalogService | Ngoài phạm vi sửa |
| Quỹ phép | DTO có reason Size1000, không NotBlank; service/write nhận reason | Có | Ngoài phạm vi sửa |
| Thay/kết thúc trưởng phòng | Form/nghiệp vụ có lý do riêng | Có qua luồng trưởng phòng | Không chỉnh |
| Từ chối/rút/hủy đơn | Rejection NotBlank, Size1000 | Theo luồng đơn hiện có | Không chỉnh |
| Thôi việc trực tiếp | DirectTerminationRequest reason NotBlank | Theo quyết định và thực thi | Không chỉnh/job không chạy |
| Hợp đồng/lương/tài khoản | Quy tắc command từng module | Tùy đường ghi | Chỉ đề xuất phần lương được nêu; không đổi chính sách rộng |

`EmployeeAuditService` vẫn giữ overload legacy để tương thích binary/source cho các phần khác, nhưng `rg` xác nhận **không còn caller sửa hồ sơ/liên hệ** dùng overload không reason. Không xóa API công khai của module khác. Creation/transfer khác phạm vi vẫn được liệt kê ở đây.

Reason tối thiểu1 tối đa1000 là quy tắc hiện có, không tự đổi thành5..500 chỉ vì prompt nêu ví dụ. Thiếu/null/trắng/quá dài bị400, không ghi. No-op không tạo audit giả. Test mới kiểm stale/invalid không gọi writer/audit và gender đổi chỉ một audit profile.
