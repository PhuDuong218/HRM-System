# ĐẶC TẢ CÔNG THỨC TIỀN LƯƠNG & MA TRẬN ĐỐI SOÁT NGHIỆP VỤ (MODULE 13 & 14)

## I. CÔNG THỨC NGHIỆP VỤ CHÍNH THỨC
1. **Lương ngày công cơ bản (Work Salary):**
   - Đơn giá ngày công = Lương cơ bản hợp đồng / Công chuẩn (22 ngày).
   - Ngày công thực hưởng = Ngày công đi làm thực tế + Ngày nghỉ phép có hưởng lương.
   - Lương ngày công = Đơn giá ngày công x Ngày công thực hưởng (làm tròn số nguyên VND).

2. **Lương làm thêm giờ (Overtime - OT):**
   - Đơn giá giờ làm việc = Đơn giá ngày công / 8 giờ.
   - Tiền làm thêm giờ = Đơn giá giờ x Số giờ OT x Hệ số làm thêm (1.5 đối với ngày thường).

3. **Phụ cấp (Allowances):**
   - Cộng trực tiếp các khoản phụ cấp hiệu lực trong kỳ (Ăn trưa: 730.000đ, Đi lại/Xăng xe: 500.000đ).
   - Phụ cấp ăn trưa được xếp vào danh mục miễn thuế TNCN (tối đa 730.000đ/tháng theo luật hiện hành).

4. **Tổng thu nhập gộp (Gross Income):**
   - Gross = Lương ngày công + Tiền tăng ca OT + Tổng phụ cấp.

5. **Các khoản giảm trừ (Deductions):**
   - **Bảo hiểm bắt buộc (10.5%):** BHXH (8%) + BHYT (1.5%) + BHTN (1%) tính trên mức lương cơ bản đóng bảo hiểm.
   - **Giảm trừ gia cảnh thuế TNCN:** 11.000.000 VNĐ/tháng đối với bản thân người lao động.
   - **Thu nhập tính thuế:** = (Gross - Phụ cấp ăn trưa miễn thuế) - Bảo hiểm bắt buộc - Giảm trừ bản thân (11 triệu).
   - **Thuế TNCN:** Tạm tính theo Biểu thuế lũy tiến từng phần (Bậc 1: đến 5 triệu chịu 5%).

6. **Lương Thực nhận (Net Pay):**
   - Net Pay = Gross - Bảo hiểm bắt buộc - Thuế TNCN.

---

## II. MA TRẬN ĐỐI SOÁT CÁC KỊCH BẢN KIỂM THỬ (TEST SCENARIOS)

| Mã kịch bản | Mô tả tình huống thử nghiệm | Dữ liệu đầu vào | Kết quả kỳ vọng (Expected) | Kết quả thực tế (Actual) | Trạng thái |
|:---:|:---|:---|:---|:---|:---:|
| **TC-01** | Nhân viên đủ công, có OT 4.5h, phụ cấp ăn trưa/đi lại | Lương CB: 15tr; Công: 21 làm + 1 phép; OT: 4.5h | Gross: 16.805.284đ<br>BH: 1.575.000đ<br>Thuế: 175.014đ<br>Net: **15.055.270đ** | Trùng khớp từng đồng | **PASS** |
| **TC-02** | Nhân viên thu nhập thấp không phải đóng thuế | Lương CB: 8tr; Đủ 22 công; Không OT | Gross: 8.000.000đ<br>BH: 840.000đ<br>Thuế: 0đ<br>Net: **7.160.000đ** | Thuế = 0đ, khấu trừ đúng 10.5% BH | **PASS** |
| **TC-03** | Khóa cứng dữ liệu bảng lương đã chốt (`CK_batches_lock`) | Chốt batch 2 (`status = DA_CHOT`) | Ghi nhận `locked_by` (ID 2), `locked_at`. Chặn mọi lệnh tính lại hoặc chỉnh sửa trực tiếp | Chặn lỗi vi phạm trạng thái khi cố tình sửa đè | **PASS** |
| **TC-04** | Tự động phát hành phiếu lương khi công bố | Chuyển batch sang `CONG_BO` (gán `publishedAt`) | Tự động sinh bản ghi trong bảng `payslips` với mã duy nhất `PS-202610-NV1-V1` | Sinh đủ phiếu lương cho từng nhân viên | **PASS** |
| **TC-05** | Tạo phiên bản bảng lương điều chỉnh | Bảng lương cũ đã chốt/công bố cần sửa OT | Sinh batch mới có `version_no = 2`, `previous_batch_id = 2`, có lý do điều chỉnh | Giữ nguyên vẹn batch cũ, tạo thành công batch điều chỉnh mới | **PASS** |
| **TC-06** | Xem phiếu lương cá nhân & Xuất PDF | Nhân viên truy cập `/payslips/1/html` | Hiển thị phiếu lương chi tiết dạng HTML chuẩn in ấn, bấm `Ctrl + P` lưu file PDF | Hiển thị đẹp mắt, in chuẩn A4 | **PASS** |