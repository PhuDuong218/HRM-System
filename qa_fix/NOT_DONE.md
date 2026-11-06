# Chưa hoàn tất / không nằm trong công tắc CÓ

1. **Chưa nghiệm thu HTTP QA-01..05 trên backend mới.** Preflight đọc hrm_center_full rồi dừng trước API ghi: msdb chưa có full backup. Không có QAFIX employee mới; QA6/DEMO không bị sửa.
2. **Chưa backup DB.** Không chạy BACKUP/RESTORE vì SQL chỉ SELECT. Không có file.bak giả hoặc tuyên bố backup thành công.
3. **Chưa restart backend2005.** Không dừng tiến trình người dùng, không đổi khóa JWT, không đổi cấu hình triển khai. Source mới và DB hiện có cần được triển khai đồng bộ trước test.
4. **Job đổi lịch chưa có cờ tắt:** ScheduleChangeExpiryJob và ScheduleChangeSchedulingConfig hiện không có ConditionalOnProperty hiệu lực. `spring.task.scheduling.enabled=false` không được coi là đảm bảo tắt khi đã EnableScheduling. Không tự sửa ngoài phạm vi.
5. **Chưa chạy lại219 kịch bản cũ.** Một số công cụ cũ tạo/sửa QA6 và gửi payload PUT thiếu version; chạy nguyên bản vi phạm giữ QA6 và hợp đồng mới. Không sửa các script cũ để nới kỳ vọng. Có verify-live.cjs QAFIX mới, chưa qua preflight để ghi.
6. Race SQL haiPUT cùng version, haiPOST bằng giống nhau; rollback/audit count sauHTTP; API roleADMIN/HR/chủ hồ sơ đầy đủ: CHƯA VERIFY live. Unit/mocks không thay cho các ca này.
7. UI409 thực giữa hai tab, reload fail do mạng, Console và HAR trước/sau: CHƯA VERIFY. Đã test lớp component bằng HttpTestingController và validation thật trên web, EN/tối. Ảnh chụp thành công lưu employee-valid-id.jpg; capture ảnh lỗi CCCD bị lỗi công cụ ở các lần sau, không dựng ảnh giả.
8. QA-06..11 chỉ diff đề xuất; QA-10 kiểm syntax TS/Angular template, không có integration. Không tuyên bố các lỗi lương đã hết500/hết nhãnVI.
9. Chỉ mục duy nhất bằng cấp là đề xuất staged migration, chưa chạy. Chưa xử lý bản trùng cũ QA6.
10. Không thêm client version cho phòng/chức vụ/quỹ/hợp đồng/bằng cấp khác. Chỉ liệt kê nguy cơ ở LOST_UPDATE_FAMILY.md.

## Cần trước vòng live tiếp

- Chủ dự án tạo full backup bằng SSMS (xem hướng dẫn trong VERIFICATION.md), giữ file ở nơi bảo mật, kiểm tra restore trên DB riêng; không tự restore đè.
- Giải quyết cách tắt job đổi lịch bằng cờ cấp tiến trình trước khi chạy. Các cờ có thật khác: `personnel.resignations.scheduler-enabled=false`, `g2.resignations.scheduler-enabled=false`, `hrm.contracts.expiry-job-enabled=false`, `hrm.attendance.missing-checkout-scan-enabled=false`, `personnel.leave-accrual.scheduler-enabled=false`. Chúng chưa tắt được job đổi lịch không có guard.
- Khởi động đúng source mới trên2005 với cùng cơ chế khóa hiện có; xác nhận API detail có `version`16hex.
- Sau các điều kiện trên, cấp credentials qua biến môi trường riêng tư, không paste vào báo cáo; chạy verify-live.cjs bằng terminal được phép. `QAFIX_RUNTIME_APPROVED=1` chỉ là xác nhận vận hành, không phải cờ có khả năng tự tắt job.
