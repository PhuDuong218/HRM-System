**Cập nhật sau sửa được phép:** đã chặn các API payroll bị lọt và sửa trần ăn trưa; 10 test mới PASS, HTTP retest 124 PASS/3 FAIL/1 quan sát. Xem [fix-verification.md](fix-verification.md). Các kết quả bên dưới là baseline để giữ lịch sử.

# Đề xuất chưa áp dụng

01: thêm gate quản trị lương theo tên permission hiện tại; cần xác nhận phạm vi HR/actor từ JWT và test service. Chưa giải quyết actorAccountId, scope HR, toàn bộ API payroll khác.

02: cap tổng miễn thuế AN_TRUA 730.000 theo spec đồ án; chưa sửa NGAY/GIO, bảo hiểm, provider, component fallback hoặc snapshot.

03: bổ sung dependency mock thiếu của context test; không làm @Autowired optional và không tắt method security. Cần chạy lại context để xác minh còn dependency/assertion nào phải cập nhật.

Đây là mô tả đề xuất lúc baseline; trạng thái áp dụng/retest mới nhất xem fix-verification.md nếu có. Clock chưa có diff vì giới hạn cuối ca chưa được chốt. Tham khảo D07 và quyết định người dùng trong open-questions.md.
