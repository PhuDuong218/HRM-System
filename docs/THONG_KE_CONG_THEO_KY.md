# Thống kê công/nghỉ/tăng ca theo kỳ

## Gọi API trong Postman

1. Khởi động lại backend sau khi cập nhật code.
2. Đăng nhập bằng tài khoản ADMIN hoặc HR có quyền `REPORT_VIEW`.
3. Tạo request **GET**:

   ```text
   http://localhost:2005/api/reports/work?fromDate=2026-09-01&toDate=2026-09-30
   ```

4. Trong Authorization chọn **Bearer Token**, dán giá trị `accessToken` từ kết quả đăng nhập. Request GET không cần body.
5. Gửi request. Kết quả dự kiến là HTTP 200 khi token, quyền và dữ liệu hợp lệ. Các chỉ tiêu trả về phụ thuộc dữ liệu thật trong kỳ.

## Quy tắc tính

Hai ngày đầu/cuối đều thuộc kỳ. Ví dụ tháng 9 dùng khoảng thời gian từ `2026-09-01 00:00` (bao gồm) đến `2026-10-01 00:00` (không bao gồm).

| Trường JSON | Dữ liệu được tính |
| --- | --- |
| `confirmedWorkDays` | Số bản ghi công có kết quả `DI_LAM`, trạng thái `DA_XAC_NHAN`, đủ người và thời điểm xác nhận; lọc theo `work_date` trong kỳ. |
| `confirmedAbsentDays` | Tương tự, với kết quả `VANG`. |
| `approvedLeaveRequests` | Số đơn `NGHI_PHEP`, trạng thái `DA_DUYET`, có khoảng nghỉ giao với kỳ. |
| `approvedLeaveHours` | Tổng thời lượng phần nghỉ giao với kỳ, đổi sang giờ. |
| `approvedOvertimeRequests` | Số đơn tăng ca `DA_DUYET`, đủ `confirmed_by`, `confirmed_at` và khoảng thời gian thực tế hợp lệ giao với kỳ. |
| `approvedOvertimeHours` | Tổng giờ tăng ca thực tế sau trừ phút nghỉ, phân bổ vào phần giao với kỳ. |

Các trường công/vắng giữ tên API hiện tại nhưng đếm **bản ghi công**, chưa phải ngày công quy đổi 8 giờ. Nếu một người có hai ca trong ngày thì có thể có hai bản ghi được đếm. Không cộng đơn nghỉ vào số bản ghi vắng.

Giờ nghỉ phép là thời lượng liên tục giữa `leave_start` và `leave_end`, giới hạn trong kỳ. Chưa loại cuối tuần, giờ ngoài lịch làm, ngày lễ hoặc thời gian nghỉ giữa ca; chưa dùng số này làm giờ nghỉ tính lương.

Một đơn kéo dài qua hai kỳ được tính vào số đơn của từng kỳ có giao thời gian. Vì thế không cộng số đơn của hai kỳ để suy ra số đơn duy nhất của kỳ gộp. Giờ được chia theo thời lượng, sau đó tổng của mỗi báo cáo được làm tròn 4 chữ số thập phân (`HALF_UP`); cộng các báo cáo đã làm tròn có thể lệch ở chữ số cuối.

Không có dữ liệu thì các chỉ tiêu bằng 0. Báo cáo vẫn lấy dữ liệu lịch sử của nhân viên đã nghỉ việc; không lọc theo trạng thái làm việc hiện tại. Endpoint hiện tổng hợp theo kỳ cho toàn bộ dữ liệu được truy vấn, chưa có bộ lọc nhân viên/phòng ban.

## Tăng ca vượt biên kỳ và phút nghỉ

DB chỉ lưu `actual_break_minutes`, không lưu thời điểm bắt đầu/kết thúc từng lần nghỉ. Theo quy tắc đã chọn, phân bổ thời gian sau trừ nghỉ theo tỷ lệ:

```text
Giờ tăng ca trong kỳ =
    max(Thời lượng thực tế cả ca - Thời lượng nghỉ, 0)
    × (Thời lượng giao với kỳ / Thời lượng thực tế cả ca)
```

Ví dụ ca thực tế từ `2026-09-30 23:00` đến `2026-10-01 02:00`, nghỉ 30 phút:

| Kỳ | Thời lượng giao | Giờ tăng ca sau phân bổ nghỉ |
| --- | --- | --- |
| Tháng 9 | 1 giờ | `0.8333` |
| Tháng 10 | 2 giờ | `1.6667` |
| Gộp cả ca | 3 giờ | `2.5000` |

Chỉ làm tròn tổng giờ cuối cùng, không làm tròn riêng từng đơn. Tính thời lượng theo mili giây, phù hợp độ chính xác `datetime2(3)` của SQL hiện tại. Mốc ngày dùng trực tiếp các giá trị thời gian lưu trong DB.

## Ví dụ đối chiếu thủ công

Các ví dụ dưới đây là kết quả theo công thức, chưa phải kết quả chạy kiểm thử trên database.

- Công `CHUA_XAC_NHAN`: không cộng công/vắng.
- Nghỉ `CHO_DUYET` hoặc `TU_CHOI`: không cộng đơn/giờ nghỉ.
- Tăng ca `DA_DUYET` nhưng chưa xác nhận thực tế: không cộng đơn/giờ tăng ca.
- Khoảng nghỉ/tăng ca kết thúc đúng `2026-09-01 00:00`: không thuộc tháng 9.
- Khoảng nghỉ/tăng ca bắt đầu đúng `2026-10-01 00:00`: không thuộc tháng 9.
- Đơn nghỉ từ `2026-09-30 22:00` đến `2026-10-01 02:00`: tháng 9 có 1 đơn, `2.0000` giờ nghỉ; tháng 10 cũng có 1 đơn, `2.0000` giờ nghỉ.
- Ca tăng ca 3 giờ, nghỉ 30 phút, nằm hoàn toàn trong kỳ: 1 đơn, `2.5000` giờ.
- `fromDate` sau `toDate`: HTTP 400. Ngày phải có định dạng `yyyy-MM-dd`. Khoảng ngày được hỗ trợ là `0001-01-01` đến `9999-12-30`, để mốc cuối không bao gồm vẫn nằm trong miền `datetime2` của SQL Server.

API giữ yêu cầu role ADMIN/HR và quyền `REPORT_VIEW`; EMPLOYEE không truy cập được báo cáo này.
