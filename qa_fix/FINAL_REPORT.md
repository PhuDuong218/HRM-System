# Kết quả sửa lỗi — 06/11/2026

**Đã áp source QA-01..05; chưa đủ điều kiện nghiệm thu HTTP.**

- QA-01: version16hex từ row_version; PUT/PATCH bắt buộc version, stale409, giữ draft/reload có kiểm soát. Không đổi schema.
- QA-02/03: POST bắt buộc email/CCCD/địa chỉ; CCCD/CMND9hoặc12số, trim, giữ0đầu; PUT/PATCH giữ trường omitted/bịẩn, chặn xóa trắng ở người được xem.
- QA-04: reasontrim1..1000 bắt buộc; bỏ đường caller audit khôngreason; profile/gender dùng một audit.
- QA-05: dedup5trường, NFC/trim/case, add+update dưới khóaemployee; không indexDB mới.
- QA-06..11: chỉ diff đề xuất, chưa áp; index chỉ script đề xuất, chưa chạy.

**Hồi quy:**67/67BE (50cũ+17mới),50/50FE (49cũ+1mới), buildPASS; initial825,95kB theoCLI, không tăng so baseline.4test mới đã đỏ trước sửa. Các diff check-only hợp lệ; QA-10 parseTS/template không lỗi, không coi là integrationPASS.

**Web thật:** đã kiểm tra form CCCD sai báođỏ,9số giữ0đầu, EN/tối dịch lỗi mới; không lưuDB. Ảnh/evidence có trong evidence/.

**Chưa verify:**9caHTTP sau sửa,219API regression, raceSQL/auditcount, dialog409hai tab, Console/HAR. Preflight đọc đúnghrm_center_full rồi dừng: chưa có bằng chứng fullbackup; job đổi lịch thiếu cờtắt; chưarestartbackendbảnmới. Không dùng unit/mock thay cho kiểm thử này.

**Dữ liệu/Git:** chỉSELECT;0QAFIXemployee,0nhân viên thiếuphâncông,0nhiềubằngchính,1ADMIN. Không sửaDEMO/QA6, không chạyjob, khôngrestore, khôngcommit/push/add. Backupfile+SHA, diff, ma trận audit, ràsoátcùnghọ và các điểm chưa làm đã lưu trong qa_fix.

Phần còn lại và điều kiện chạy tiếp: NOT_DONE.md, VERIFICATION.md. Không đánh dấu task hoàn thành toàn bộ.
