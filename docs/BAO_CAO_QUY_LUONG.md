# Báo cáo quỹ lương, phụ cấp, thưởng và khấu trừ

## API và quyền truy cập

```http
GET http://localhost:2005/api/reports/payroll?fromDate=2026-08-01&toDate=2026-08-31
```

- Dùng Bearer Token của ADMIN hoặc HR.
- Phải đồng thời có `REPORT_VIEW` và `PAYROLL_VIEW`. Chỉ có một trong hai quyền không đủ; `PAYROLL_MANAGE`, `SELF_READ`, `PAYSLIP_SELF_READ` không thay thế được.
- EMPLOYEE không được truy cập, kể cả khi bị gán nhầm hai quyền này.
- Kiểm tra quyền áp dụng tại cả controller và service. Danh tính/quyền được nạp qua cơ chế JWT đang có; API không nhận role hay employeeId từ client.
- Đây là báo cáo quản trị toàn trung tâm theo thiết kế hiện tại. Phạm vi HR phân lịch trong tài liệu `hr_department_scopes` không được diễn giải thành quyền báo cáo lương.
- Response chỉ có dữ liệu tổng hợp và thông tin kỳ/batch, không trả lương từng nhân viên, định danh nhân viên, giải thích dòng tiền hoặc toàn bộ JSON snapshot. Response có `Cache-Control: no-store`.

## Kỳ và phiên bản được tính

`fromDate`, `toDate` là hai ngày bao gồm, định dạng `yyyy-MM-dd`. Chỉ chọn **nguyên kỳ lương nằm trọn trong khoảng này**:

```text
period_start >= fromDate
period_end <= toDate + 1 ngày
```

Trong schema, `period_end` là mốc không bao gồm: kỳ tháng 8 bắt đầu `2026-08-01`, kết thúc `2026-09-01`. Request `2026-08-01` đến `2026-08-31` lấy nguyên kỳ tháng 8; request `2026-08-10` đến `2026-08-20` không lấy kỳ này. Không chia tỷ lệ số tiền đã chốt theo ngày giao với bộ lọc. Response ghi rõ `periodEndExclusive`.

Mỗi kỳ chỉ chọn batch có `version_no` cao nhất trong các bản `status = 'DA_CHOT'`, đủ `locked_by` và `locked_at`:

- Không bao giờ tính `NHAP`; client không có tham số mở rộng sang bản nháp.
- Một bản nháp mới hơn không che bản đã chốt cũ.
- Khi có nhiều bản đã chốt, không cộng dồn các phiên bản.
- Báo cáo quản trị không yêu cầu batch đã công bố. Công bố cho nhân viên là bước riêng qua `published_by`/`published_at`.

**Quy ước phiên bản:** bản điều chỉnh mới nhất đã chốt chứa toàn bộ kết quả thay thế của kỳ, không phải chỉ các khoản chênh lệch. Đây là quy ước báo cáo theo thiết kế phiên bản hiện tại; schema không tự kiểm tra đủ bộ nhân viên của bản thay thế. Quy trình tạo batch điều chỉnh phải tuân thủ quy ước này. Trả `batchId`, `versionNo` để đối chiếu bản được chọn.

## Nguồn và cách tính tiền

Chuỗi dữ liệu: `payroll_periods → payroll_batches → payroll_details → payroll_lines`.

Chỉ dùng `payroll_lines.amount`, `component_code_snapshot` và `direction_snapshot`. Không tính lại từ lương hợp đồng, danh mục thành phần, phụ cấp hoặc quyết định hiện tại; không lọc bỏ nhân viên đã nghỉ việc khỏi lương lịch sử.

| Trường trong `totals` | Cách tính |
| --- | --- |
| `grossPayroll` | Tổng mọi dòng có `direction_snapshot = ADD`: tổng thu nhập trước khấu trừ. |
| `allowances` | Tổng dòng `ADD` và `component_code_snapshot = PHU_CAP`. |
| `bonuses` | Tổng dòng `ADD` và `component_code_snapshot = THUONG`. |
| `deductions` | Tổng mọi dòng `DEDUCT`, bao gồm `KHAU_TRU`, `BAO_HIEM` và các mã khấu trừ khác. |
| `netPayroll` | `grossPayroll - deductions`. |

Phụ cấp và thưởng **đã nằm trong grossPayroll**, không cộng thêm lần nữa. Mã ADD khác như `LUONG_CONG`, `LUONG_CO_BAN`, `TANG_CA`, `DIEU_CHINH` vẫn được tính vào grossPayroll. Mã khấu trừ khác vẫn được tính theo hướng DEDUCT. Hiện không có cột phân loại nhóm khoản trong schema, nên phụ cấp/thưởng dùng đúng hai mã snapshot đã có, không đoán từ tên hoặc tiền tố mã.

SQL dùng `decimal`, Java dùng `BigDecimal`, không dùng số thực và không làm tròn về số nguyên. Báo cáo giữ số tiền đã chốt đến 4 chữ số thập phân; không giới hạn thực nhận tối thiểu 0 nếu dữ liệu đã chốt cho ra giá trị âm.

## Dạng kết quả

Ví dụ minh họa cấu trúc, không phải số liệu lấy từ DB đang chạy:

```json
{
  "fromDate": "2026-08-01",
  "toDate": "2026-08-31",
  "batchCount": 1,
  "payrollRecordCount": 2,
  "totals": {
    "grossPayroll": 13700000.0000,
    "allowances": 700000.0000,
    "bonuses": 0.0000,
    "deductions": 200000.0000,
    "netPayroll": 13500000.0000
  },
  "batches": [
    {
      "periodId": 1,
      "periodCode": "2026-08",
      "periodStart": "2026-08-01",
      "periodEndExclusive": "2026-09-01",
      "batchId": 1,
      "versionNo": 1,
      "employeeCount": 2,
      "totals": {
        "grossPayroll": 13700000.0000,
        "allowances": 700000.0000,
        "bonuses": 0.0000,
        "deductions": 200000.0000,
        "netPayroll": 13500000.0000
      }
    }
  ]
}
```

`employeeCount` đếm nhân viên trong một batch, không bị tăng theo số dòng tiền. `payrollRecordCount` là tổng số kết quả nhân viên/kỳ của các batch được chọn: một người xuất hiện ở hai kỳ sẽ được tính hai lần. `batchCount` là số batch đã chọn, mỗi kỳ tối đa một batch.

Không có kỳ/batch đã chốt thỏa bộ lọc thì trả HTTP 200, `batchCount = 0`, `payrollRecordCount = 0`, các tổng bằng 0 và `batches = []`.

## Đối chiếu bằng Postman

1. Khởi động lại backend. Đăng nhập lấy token mới của tài khoản ADMIN/HR có đủ hai quyền.
2. Chọn GET, dán URL ở đầu tài liệu. Authorization chọn Bearer Token, dán riêng giá trị `accessToken`; không gửi body.
3. Với bộ lọc đúng kỳ có batch đã chốt: đối chiếu `batchId/versionNo`, tổng ADD, phụ cấp, thưởng, tổng DEDUCT và thực nhận với dòng snapshot của batch đó.
4. Với kỳ chỉ có NHAP: kỳ vọng danh sách rỗng và tổng 0, dù dòng lương nháp có số tiền.
5. Với kỳ có bản chốt v1, bản nháp v2: kỳ vọng vẫn chỉ có v1. Với v1 và v2 cùng đã chốt: kỳ vọng chỉ có v2.
6. Dùng tài khoản thiếu `REPORT_VIEW`, thiếu `PAYROLL_VIEW`, hoặc EMPLOYEE: kỳ vọng 403 và không có dữ liệu báo cáo lương.
7. Dùng `fromDate` sau `toDate`: kỳ vọng 400. Phạm vi ngày hỗ trợ là `0001-01-01` đến `9999-12-30`, để mốc cuối không bao gồm nằm trong miền ngày SQL Server.

Đây là hướng dẫn đối chiếu, không phải kết quả kiểm thử đã chạy. Seed chính tạo nhiều batch NHAP; thấy tổng 0 không có nghĩa được phép tự chốt batch. Script tùy chọn `seed_phieu_luong_an_phuc_ONLY_EMP_TUY_CHON.sql` có bước chốt batch giả lập tháng 8, chỉ sử dụng theo điều kiện của script trên DB thử nghiệm riêng; chức năng báo cáo không chạy script hoặc thay trạng thái batch.

Hai API lương cá nhân `/api/me/salary`, `/api/me/payslips` đã được đồng bộ điều kiện `DA_CHOT` cùng metadata chốt và công bố hợp lệ. API cá nhân chỉ lấy dữ liệu của chính người gọi và giữ lịch sử các bản đã công bố. Báo cáo quản trị ở tài liệu này dùng bản chốt mới nhất mỗi kỳ, không yêu cầu công bố; hai phạm vi chọn dữ liệu khác nhau.
