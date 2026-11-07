# Kiểm chứng trước–sau

## Trạng thái nghiệm thu

**SOURCE/UNIT ĐÃ QUA; LIVE HTTP CHƯA VERIFY. Không đánh dấu toàn bộ task hoàn thành.**

| Ca FAIL gốc | Trước — HTTP lượt trước | Sau — bằng chứng hiện có | HTTP mới |
|---|---|---|---|
| D11-stale-profile-must-not-silently-overwrite |200, tên cũ ghi đè| VersionFix test reject stale409/no audit; FE giữ draft/reload | CHƯA VERIFY |
| D5-required-private-contact-fields |201 thiếu3trường| Validator test thiếu/null/blank/trắng bị lỗi cả3 | CHƯA VERIFY |
| D5-invalid-ID-letters |200 CCCDabc| DTO/helper9/12 số; web báo đỏ và EN; số0đầu được giữ trên form | CHƯA VERIFY lưu/đọcDB |
| D11-update-requires-reason |200 thiếureason| DTO/wrapper test400/no mutate/no audit | CHƯA VERIFY |
| D15-identical-degree |201 bảntrùng| Service test409 trướcsave; case/Unicode/update/different/removed | CHƯA VERIFY raceSQL |
| duplicate-period |500| Chỉ diffQA-06 | CHƯA ÁP |
| reversed-period |500| Chỉ diffQA-07 | CHƯA ÁP |
| K8-publish-before-lock |500| Chỉ diffQA-08 | CHƯA ÁP |
| K6-lock-before-calculation |500| Chỉ diffQA-09 | CHƯA ÁP |

QA-10/11 giữ baseline, chỉ đề xuất. Không tính thành PASS sau sửa.

## Hồi quy

| Yêu cầu | Kết quả |
|---|---|
| 49 FE cũ |49/49PASS, test cũ không sửa |
| FE mới |1/1PASS, test nhiều assertion cho version/draft/reload/9–12/EN |
| 50 BE cũ |50/50PASS trên9suite unit/mocked đã dùng lượt trước |
| BE mới |17/17PASS trên4class mới (4+5+4+4) |
| Đỏ trước sửa |4/4test EmployeeWriteFixRegressionTest FAIL; xemunit-before.log |
| Production build |PASS; initial825,95kB theoCLI, bằng baseline825,95; còn warning750kB |
|219API cũ |CHƯA CHẠY LẠI; phải giữQA6/DEMO và bổ sungversion ở harness mới, không nới test cũ |
| GUI thật |ADMIN đăng nhập; mở create; CCCDabc hiện đỏ;099999999 hết lỗi và giữ0đầu; EN/tối thông báo đúng; discarddraft và khôi phụcVI/sáng |
| Dialog4092tab |Class/harness PASS; browserthật với backendmới CHƯA VERIFY |
| Console/HAR |CHƯA VERIFY đầy đủ; build/harness không chứng minh Console/HAR |
| SQLcuối |hrm_center_full;0 nhân viên thiếuphâncông;0 nhiều bằng chính;1ADMIN;0QAFIXemployee |
| Ghi dữ liệu |Không tạo/sửaDEMO/QA6/QAFIX trongDB lượt này; chỉSELECT vàlogin/readUI |
| Backup |msdb last_full_backup NULL; chưa có bằng chứng fullbackup |

`backend-summary.json` liệt kê suite, sốtest/failure/error; `frontend-final.log`, `frontend-build-final.log` giữ output. `live-preflight.log` chứng minh dừng trướcAPImutation vìbackup. Không dùng log của lần67test trước để che lỗi ở lần sau: suite/filelog cuối được cập nhật sau lần chạy cuối.

## Tạo backup và phục hồi — hướng dẫn, CHƯA THỰC HIỆN

1. Trong SSMS chọn đúng `hrm_center_full`, thao tác Back Up loại Full theo quy trình quản trị, lưu file.bak riêng có dấu thời gian; không ghi đè backup duy nhất.
2. Kiểm tra backup hoàn tất, kiểm tra media/checksum theo công cụ quản trị; lưu bằng chứng thời gian/DB, không lưu chuỗi kết nối hoặc credentials trong qa_fix.
3. Kiểm chứng phục hồi bằng **một DB riêng**, ví dụ `hrm_center_full_restore_check`. Xem logical filenames trong backup, đặt fileMDF/LDF đích khác file củaDBgốc; không dùng WITH REPLACE lênDBgốc.
4. So bảng/sốbản ghi/bất biến trên DBrestorecheck. Không tự trỏ ứng dụng sang bảnrestore.
5. Nếu sau này cần restoreDBgốc, chủ dự án lên lịch dừng ứng dụng/kết nối, xác nhận backup và phương án phục hồi trước; agent không tự restore. Các lệnh BACKUP/RESTORE không được chạy trong lượt này vì giới hạn SQL chỉSELECT.

## Cách chạy lại sau khi đủ điều kiện

- Khởi động source mới trên2005, bảo toàn cơ chếJWT; giải quyết tắt job theo NOT_DONE.md trước.
- Credential chỉ đưa vào biến môi trường terminal riêng: QAFIX_ADMIN_PASSWORD, QAFIX_HR_PASSWORD; username tùy chọn QAFIX_ADMIN_USER/QAFIX_HR_USER. Không ghi giá trị vào file hoặc chat.
- Xác nhận vận hành bằng QAFIX_RUNTIME_APPROVED=1 chỉ sau backup/recovery/jobcheck. Biến này không tự tắt job.
- Chạy từ root: `node qa_fix/verify-live.cjs`. Script SELECT xác nhận DB/backup và GET version trước mọi POST. Khi qua gate, mọi hồ sơ thử có prefixQAFIX; không sửaQA6/DEMO.
- Script không bao trọn219ca. Sau đó cần harness regressionQAFIX đủ219ca, các role, dialog2tab, Console/HAR; giữ các ca nghiệp vụ lương off-switch chưaáp thànhCHƯAÁP.

## Ảnh / bằng chứng UI

Ảnh `evidence/employee-valid-id.jpg`: bản nháp form chấp nhậnCMND9số, không lưuDB. `ui-en-dark.txt`: accessibilitystate lỗi mới đã dịch khiEN/tối. Ảnh lỗi invalid chưa lưu được do capturetool không ổn định; không coi là thiếu validation vì AX vàharness đã ghi nhận thông báo.
