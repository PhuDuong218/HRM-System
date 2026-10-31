# Đối soát lương

Oracle dùng Python Decimal, làm tròn HALF_UP, 22 công/8 giờ, OT 1,5, bảo hiểm 10,5%, giảm trừ 11 triệu theo spec đồ án. NGÀY/GIỜ quy đổi căn cứ tháng và phân bổ phụ cấp theo quyết định người dùng. Không thẩm định pháp luật.

**Chỉ đối chiếu engine với đầu vào kiểm soát, chưa phải đối soát dữ liệu SQL → batch → phiếu lương.** Provider runtime giả là blocker cho kết luận này.

| Ca | Gross kỳ vọng | BH kỳ vọng | Net kỳ vọng | Net thực tế | Chênh lệch | Kết quả |
|---|---:|---:|---:|---:|---:|---|
| TC-01 | 16805284 | 1575000 | 15055270 | 15055270 | 0 | PASS |
| TC-02 | 8000000 | 840000 | 7160000 | 7160000 | 0 | PASS |
| TC-03 | 30000000 | 3150000 | 25222500 | 25222500 | 0 | PASS-THEO-CODE |
| TC-04 | 7500000 | 1575000 | 5925000 | 5925000 | 0 | PASS |
| TC-05 | 23500000 | 2310000 | 20411500 | 20411500 | 0 | PASS-THEO-CODE |
| TC-06 | 10800000 | 1050000 | 9750000 | 9750000 | 0 | PASS |
| TC-07 | 20000000 | 2100000 | 17460000 | 17460000 | 0 | PASS-THEO-CODE |
| TC-08 | 60000000 | 6300000 | 46275000 | 46275000 | 0 | PASS-THEO-CODE |
| MEAL-CAP-01 | 15800000 | 1575000 | 14100250 | 14103750 | 3500 | FAIL |
| DAILY-01 | 22000000 | 2310000 | 19071000 | 895000 | -18176000 | FAIL |
| HOURLY-01 | 17600000 | 1848000 | 15514400 | 89500 | -15424900 | FAIL |

TC-03/05/07/08: PASS-THEO-CODE chỉ khớp thuật toán bậc 2–5 hiện có; chưa có kỳ vọng nghiệp vụ chính thức để nghiệm thu. DAILY-01 không dùng net bậc 2 làm kết luận tiền lương cuối cùng; gross và BH đã sai độc lập.

Chưa kiểm chứng pipeline thực với đổi hợp đồng giữa kỳ, phụ cấp một phần kỳ, correction đã duyệt, nghỉ/OT, tính lại idempotent, lock/publish, điều chỉnh và tổng batch khớp phiếu. Cần làm sau khi có provider thật; không cộng những ca này vào PASS.

Sau bản sửa được phép: MEAL-CAP-01 PASS (delta 0), TC-01…08 không đổi; DAILY-01/HOURLY-01 vẫn FAIL. Xem evidence/payroll-oracle-retest.json.
