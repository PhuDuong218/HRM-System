# Kết quả kiểm thử HRM Center

**Chưa sẵn sàng bàn giao.** Có hai lỗi S1 đã tái hiện: HR đọc quỹ phép ngoài phạm vi và logout500 không thu hồi token. Chỉ báo cáo, chưa sửa mã nguồn.

- Repo: XuanAnh / fb93765, kiểm working tree có thay đổi sẵn. Source/test hash đầu-cuối không đổi.
- API: 685 lượt, 669 PASS, 10 FAIL, 6 OBSERVED. Đây là số request, không phải số chức năng nghiệm thu. /hello và danh mục nghỉ cho EMPLOYEE là quan sát cần chốt/được giải thích, không tự coi lỗi bảo mật.
- Java có chọn lọc:526 test —399 PASS,120 FAIL,6 ERROR,1 SKIP. Nhiều ca do fixture/config cũ; xem phân loại. 12 lớp có nguy cơ SQL fixture không chạy. Không tuyên bố full suite đạt.
- UI:107 lượt mở route —77 Tier1 tại7 độ rộng (gồm6 mốc yêu cầu),30 smoke; chưa thấy tràn ngang toàn trang. Chỉ một drawer được mở ở390; chưa kiểm mọi form/CRUD ở mọi kích thước. Build Angular PASS.
-165 hàng checklist gốc đều có disposition trong CSV:7 FAIL (có nhánh lỗi),158 NOT-RUN đủ toàn hàng; nhiều subcase đã chạy bên dưới. Không coi một request PASS là cả hàng đã hoàn thành. BLOCKED nêu bằng lý do trong OPEN_QUESTIONS và ENVIRONMENT.

| Lỗi | Mức | Task | Kết quả chính |
|---|---|---|---|
| BUG-001 | S1 | FE-006 | HR ngoài phạm vi đọc được số dư phép của nhân viên phòng khác |
| BUG-002 | S1 | FE-001 | Đăng xuất trả 500 và không thu hồi token đang dùng |
| BUG-003 | S3 | FE-005 / FE-006 | Danh mục trình độ và loại nghỉ không có bộ lọc hoặc Xem tất cả |
| BUG-004 | S3 | FE-001 / Tier 2 accounts | Trang tài khoản giữ nhãn tiếng Việt khi chọn EN |
| BUG-005 | S3 | FE-009 / Tier 2 schedules | Thiếu bộ lọc ngày tra cứu lịch trả 401 thay vì lỗi dữ liệu 400 |

Theo mức: S1=2, S2=0 đã xác minh, S3=3, S4=0. Theo task: FE001=2, FE005/FE006 dùng chung=1, FE006=1, FE009/Tier2=1 (lỗi dùng chung không cộng hai lần).

Bốn UI còn placeholder: phụ cấp, khen thưởng/kỷ luật, tiền lương, phiếu lương. Không tính là lỗi chạy, không xác nhận hoàn thành. /accounts và /reports có UI thật.

Cần sửa/kiểm lại5 lỗi, rà test fixture/cấu hình, chốt rủi ro job lúc khởi động, rồi chạy các luồng A–F đầy đủ trước nghiệm thu. Chưa làm xong kiểm thử toàn bộ prompt: lịch nghỉ phép hợp lệ có giờ công, job tới hạn, race duyệt đơn, rollback ép lỗi,100 nhân viên QA, đối chiếu mọi cột/ràng buộc, Edge độc lập và mọi form responsive đều chưa đủ bằng chứng.

**Rủi ro cần đối chiếu:** một số SpringBootTest đã đọc cấu hình hrm_db rồi bị guard chặn trước fixture; job ApplicationReadyEvent có thể chạy trước guard. Không có baseline DB chính nên không thể bảo đảm không có tác động tự động; không tự restore. Các helper kiểm thử API/SQL của phiên dùng hrm_test đã backup. Chi tiết ENVIRONMENT.md.

Báo cáo cá nhân trong qa_tester; không dùng git add . để đẩy cả thư mục này.
