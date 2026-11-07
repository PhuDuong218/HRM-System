# Điểm cần chốt và giới hạn

1. Đề cương mục9.1 không có nội dung đầy đủ (chỉ còn dòng 4 trước9.2). Các nhánh nộp đơn chỉ đối chiếu API hiện có, không tự viết lại đặc tả.
2. /hello đang công khai theo SecurityConfig, trả lời chẩn đoán không chứa dữ liệu nhạy cảm. Tester prompt chỉ cho health/login công khai; cần chốt ngoại lệ. Đã ghi OBSERVED, không gán lỗi vượt quyền nhân sự.
3. EMPLOYEE đọc danh mục loại nghỉ là cần để nộp đơn; hai kỳ vọng403 ban đầu của script đã được loại khỏi lỗi sản phẩm, giữ lịch sử OBSERVED.
4. Không chạy execute-due toàn cục vì có thể xử lý đơn có sẵn của nhóm. Chưa mô phỏng tới ngày tương lai/job sau nghỉ việc.
5. Các test SQL Server có fixture SQL ghi trực tiếp không thỏa luật API-only/QA_ của phiên này; nhóm bị loại nằm trong java-selector.json.
6. Một số @SpringBootTest không pin profile test đã kết nối cấu hình hrm_db và dừng bởi SELECT DB_NAME guard trước fixture. Có ContractExpiryJob.onReady chạy khi ApplicationReadyEvent; chưa có snapshot trước của hrm_db nên KHÔNG KHẲNG ĐỊNH không có tác động tự động. Không chạy thêm nhóm này, không tự restore hrm_db. Đây là rủi ro môi trường cần nhóm đối chiếu audit/backup riêng.
7. Runtime QA vẫn chọn MockSalaryContractProvider / MockAttendanceDataProvider (đã biết D02, tái quan sát wiring hiện tại). Chưa tính lương ghi để tránh dùng nguồn giả và không đào sâu Tier2.
8. Các quyết định thuế bậc2+, người phụ thuộc, OT nghỉ/lễ và sàn/trần bảo hiểm theo tester prompt là chưa đặc tả; không đưa ra kết luận pháp luật hoặc lỗi công thức trong lượt này.
9. Ngày cấp/hết hạn/tệp bằng cấp không có trong đề cương5.2 đọc được; không tự coi là lỗi bắt buộc của đề cương.
10. Không đo được phiên bản binary của trình duyệt tích hợp; chưa kiểm Edge riêng. Ảnh chụp của công cụ có vấn đề tỷ lệ hiển thị nên kết luận responsive giới hạn ở DOM/CSS, không chứng nhận mọi pixel.
11. Bước DELETE phòng ban/chức vụ bị kiểm duyệt tự động chặn vì không xác minh quyền sở hữu QA tại bước đó; đã loại khỏi lượt chạy tiếp, chưa vô hiệu hóa/xóa phòng ban hoặc chức vụ bằng bước này.
