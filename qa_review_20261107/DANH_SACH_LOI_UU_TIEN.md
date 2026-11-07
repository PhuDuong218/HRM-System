# Danh sách phát hiện cần xử lý

Phạm vi: hai ZIP; ngày 07/11/2026. Đây là các lỗi, thiếu nghiệp vụ và rủi ro thiết kế, không phải tất cả đều là lỗi đã tái hiện.

[Báo cáo đầy đủ và 113 chức năng](./BAO_CAO_DANH_GIA_HRM.md) · [Kiểm kê API](./API_INVENTORY.md)

| Mã | Ưu tiên | Phát hiện | Mức chắc chắn |
|---|---|---|---|
| L01 | P0 | Tính lương dùng nhân viên và số liệu giả | Xác định từ code |
| L02 | P1 | Thuế TNCN sai phiên bản chính sách 2026 | Xác định từ code + nguồn chính thức |
| L03 | P1 | OT và giờ làm ban đêm bị tính chung hệ số 1,5 | Xác định từ code + nguồn chính thức |
| L04 | P1 | Bảo hiểm luôn trừ 10,5% lương cơ bản | Xác định từ code + đối chiếu pháp luật |
| L05 | P1 | Mức lương theo ngày/giờ và thay đổi giữa kỳ chưa vào kết quả | Xác định từ code |
| L06 | P1 | Phụ cấp và thưởng/bồi thường chưa nối vào engine thật | Xác định từ code |
| L07 | P1 | Hai quy ước ngày kết thúc kỳ mâu thuẫn | Xác định từ code; cần test tích hợp cho dữ liệu thực |
| L08 | P1 | Điều chỉnh lương bị khóa bởi cửa sổ ngày 3–5 | Xác định từ code; quy tắc nội bộ cần xác nhận |
| L09 | P1 | Bản điều chỉnh là bản tính lại, chưa phải bù trừ chi trả | Xác định từ code và mô hình API |
| L10 | P1 | Chốt lương chưa chứng minh kết quả còn khớp nguồn | Rủi ro suy ra từ code; cần tái hiện tích hợp |
| L11 | P2 | Snapshot JSON ghép chuỗi không escape | Xác định từ code; lỗi dữ liệu cụ thể cần tái hiện |
| L12 | P2 | Thiếu danh mục thành phần lương bị tự gán ID 1 và chiều ADD | Xác định từ code |
| L13 | P1 | In phiếu từ bảng lương không gắn kỳ và phiên bản đang chọn | Xác định từ code |
| L14 | P2 | Giao diện lương không nhận biết đã công bố | Xác định từ code |
| L15 | P2 | Quyền xem lương không khớp endpoint frontend dùng | Xác định từ annotation và frontend |
| L16 | P2 | Tải dữ liệu lương có nguy cơ lẫn kỳ và che lỗi | Xác định cấu trúc bất đồng bộ; cần test trình duyệt |
| L17 | P2 | Mã nhân viên trên bảng/phiếu lương được tự dựng từ ID | Xác định từ code |
| L18 | P1 | Web chặn nhân viên thường dù backend có API tự phục vụ | Xác định trong frontend ZIP; chưa đánh giá ứng dụng mobile |
| L19 | P1 | Thiếu kiểm soát OT theo tháng/năm và lịch lao động đầy đủ | Chưa tìm thấy trong phạm vi source đã rà |
| L20 | P2 | Quy định chấm công và duyệt đơn khó thay đổi theo doanh nghiệp | Xác định từ code; không tự coi là vi phạm luật |
| L21 | P2 | Tổ chức chỉ có danh sách phòng phẳng và một phân công hiệu lực | Xác định từ model/schema |
| L22 | P2 | Hợp đồng là sổ ghi nhận đã ký, chưa có vòng soạn–duyệt–ký | Xác định từ code và API |
| L23 | P2 | Quy tắc thử việc chặn theo người, thiếu theo công việc | Xác định từ code + đối chiếu yêu cầu nghiệp vụ |
| L24 | P1 | Nghỉ việc mới hoàn tất trạng thái nhân sự, chưa có thanh toán/bàn giao đầy đủ | Xác định chức năng hiện có; thiếu phần nghiệp vụ thị trường |
| L25 | P2 | Quỹ phép có cấp theo năm, chưa thấy sổ chuyển/hết hạn/thanh toán phép | Xác định API hiện có; phần thiếu cần thiết kế |
| L26 | P1 | Một ADMIN vừa quản trị hệ thống vừa là điểm duy nhất tính/chốt lương | Xác định từ code; hạn chế thiết kế kiểm soát nội bộ |
| L27 | P2 | Màn hình phân quyền mới xem vai trò/sửa mô tả quyền | Xác định từ API và UI |
| L28 | P1 | ZIP chứa cấu hình local có trường mật khẩu DB không rỗng | Xác định sự hiện diện; không xác nhận mật khẩu còn dùng |
| L29 | P2 | Lỗi nghiệp vụ lương bị trả thành lỗi máy chủ 500 | Xác định từ code |
| L30 | P2 | UI thiếu quản lý kỳ lương/thành phần lương dù backend có API | Xác định qua routes và service frontend |
| L31 | P2 | Bố trí thiếu trang hồ sơ 360 và hộp việc cần làm tập trung | Nhận xét thiết kế dựa trên template/routes |
| L32 | P2 | Một số trang chưa dùng cùng chuẩn tương tác và khả năng truy cập | Nhận xét từ code; chưa xác minh bố cục bằng trình duyệt |
| L33 | P2 | Tài liệu hợp đồng/phiếu lương chưa có quy trình xuất và chứng cứ đầy đủ | Xác định qua API/template |
| L34 | P2 | Kiểm thử và vận hành chưa đủ chứng minh sẵn sàng triển khai | Xác định từ repo và kết quả kiểm tra phiên này |

P0: chặn dùng kết quả thật. P1: ưu tiên sai tiền, quyền và đứt luồng. P2: hoàn thiện nghiệp vụ/độ tin cậy/trải nghiệm.
