package com.hrm.constant;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * P-01: Quy tắc và hằng số chính sách tiền lương đồ án HRM
 */
public final class PayrollConstants {

    private PayrollConstants() {
        // Chặn khởi tạo đối tượng
    }

    // --- 1. TỶ LỆ TRÍCH ĐÓNG BẢO HIỂM BẮT BUỘC (PHẦN NHÂN VIÊN ĐÓNG) ---
    public static final BigDecimal BHXH_RATE = new BigDecimal("0.08");  // 8.0%
    public static final BigDecimal BHYT_RATE = new BigDecimal("0.015"); // 1.5%
    public static final BigDecimal BHTN_RATE = new BigDecimal("0.01");  // 1.0%
    public static final BigDecimal TOTAL_INSURANCE_RATE = new BigDecimal("0.105"); // 10.5%

    // --- 2. MỨC GIẢM TRỪ GIA CẢNH THUẾ TNCN (VND/tháng) ---
    public static final BigDecimal GIAM_TRU_BAN_THAN = new BigDecimal("11000000"); // 11 triệu VNĐ
    public static final BigDecimal GIAM_TRU_NGUOI_PHU_THUOC = new BigDecimal("4400000"); // 4.4 triệu VNĐ/người

    // --- 3. QUY TẮC CÔNG VÀ LÀM TRÒN ---
    public static final int DEFAULT_STANDARD_WORK_DAYS = 22; // Số ngày công chuẩn mặc định
    public static final RoundingMode DEFAULT_ROUNDING_MODE = RoundingMode.HALF_UP; // Quy tắc làm tròn số
    public static final int MONEY_DECIMAL_SCALE = 0; // Đơn vị VND: làm tròn đến hàng đơn vị (không lấy số thập phân)
}