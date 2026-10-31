**Cập nhật sau sửa được phép:** đã chặn các API payroll bị lọt và sửa trần ăn trưa; 10 test mới PASS, HTTP retest 124 PASS/3 FAIL/1 quan sát. Xem [fix-verification.md](fix-verification.md). Các kết quả bên dưới là baseline để giữ lịch sử.

# Thứ tự xử lý và kiểm thử lại

1. **P0 — Quyền lương:** người phụ trách payroll bổ sung role/permission backend cho cả period/batch; actor lấy từ principal. Test ADMIN có quyền, ADMIN thiếu permission, HR đúng/ngoài scope, EMPLOYEE và anonymous; assert không có DB write khi bị chặn.
2. **P0 — Nguồn lương thật:** thay provider giả ở môi trường nghiệm thu; nguồn hợp đồng, phân công, công/OT/nghỉ đã duyệt phải theo kỳ/ngày hiệu lực. Không dùng data giả để chốt một batch thật.
3. **P1 — Engine:** căn cứ THANG/NGAY/GIO và BH quy đổi theo quyết định nhóm; cap tổng AN_TRUA 730.000 theo spec; fail danh mục component thiếu; serialize JSON bằng thư viện. Chốt bậc thuế >1 trước kết luận tiền cuối cùng.
4. **P1 — Chấm công:** sớm 30; sau 15 ghi thực tế và flag muộn. Chốt giới hạn check-in sau hết ca; không biến mất dữ liệu chấm công bằng việc chặn giờ đến muộn. Test biên tới giây/phút, ca qua đêm, ownership, concurrency.
5. **P1 — Regression:** sửa harness/fixture đúng hợp đồng; không bỏ assertion hoặc hạ bảo mật. Chạy lại nhóm liên quan rồi toàn bộ suite.
6. **P1 — Dữ liệu khóa:** test giao dịch QA riêng cho INSERT/UPDATE/DELETE tiền lương/snapshot/details sau lock, idempotency/adjust và DB trigger. Đối chiếu tổng batch/detail/phiếu bằng oracle SQL độc lập.
7. **P2 — UI:** hoàn tất 6 placeholder theo người sở hữu; form CRUD/validation/mobile drawer/buttons/VI-EN từng thông báo, chứ không chỉ route/build.
8. **P2 — Vận hành:** migration bootstrap sạch, restore drill, rotation mật khẩu cũ nếu chưa làm; dependency upgrade có review; tách cấu hình secret khỏi source.

Các patch ghi nhận đề xuất ở thời điểm baseline. Trạng thái áp dụng mới nhất xem fix-verification.md nếu có; không sửa hàng loạt ngoài các lỗi người dùng đã cho phép.

Các lệnh automation có thể tạo dữ liệu QA, phải chạy riêng trên hrm_test với backup; đọc script trước khi chạy lại. Không dùng chúng với hrm_db. Runtime QA tắt scheduler để không tạo side effect ngoài ca chủ động; điều đó đồng nghĩa chưa kiểm chứng job tự chạy.
