package com.hrm.export;

import com.hrm.dto.ContractStatisticsResponse;
import com.hrm.dto.EmployeeStatisticsResponse;
import com.hrm.dto.PayrollStatisticsResponse;
import com.hrm.dto.PayrollStatisticsResponse.Amounts;
import com.hrm.dto.WorkStatisticsResponse;
import com.hrm.repository.projection.EmployeeStatisticsRow;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/** Writes only the fields already exposed by an authorized report response. */
@Component
public class ReportExcelWriter {

    public byte[] employees(EmployeeStatisticsResponse report) {
        return write(book -> {
            book.filters("Thống kê nhân viên", List.of(
                    row("Phạm vi bộ lọc", "Báo cáo hiện tại không có tham số lọc."),
                    row("Phân công hiện tại", "effective_from <= ngày chạy báo cáo < effective_to; effective_to có thể để trống."),
                    row("Chưa có phân công", "Nhân viên chưa có phân công hiệu lực được xếp nhóm CHUA_PHAN_CONG."),
                    row("Phân nhóm", "Phòng ban, chức vụ, trạng thái, trình độ")));
            book.employeeGroups("Theo phong ban", report.byDepartment());
            book.employeeGroups("Theo chuc vu", report.byPosition());
            book.employeeGroups("Theo trang thai", report.byStatus());
            book.employeeGroups("Theo trinh do", report.byQualification());
        });
    }

    public byte[] contracts(ContractStatisticsResponse report) {
        return write(book -> {
            book.filters("Thống kê hợp đồng", List.of(
                    row("Từ ngày (fromDate)", report.fromDate().toString()),
                    row("Đến ngày (toDate)", report.toDate().toString()),
                    row("Ngày báo cáo (reportDate)", report.reportDate().toString()),
                    row("Phòng ban (departmentId)", report.departmentId() == null
                            ? "Tất cả phòng ban" : report.departmentId().toString()),
                    row("Phân công dùng lọc phòng ban", "Có phần thời gian chung giữa phân công, kỳ lọc và hợp đồng; mỗi hợp đồng chỉ đếm một lần."),
                    row("Số ngày sắp hết hạn (expiringWithinDays)", report.expiringWithinDays())));
            book.table("Chi tieu", new String[]{"Chỉ tiêu", "Số hợp đồng"}, List.of(
                    row("Tổng hợp đồng", report.contractCount()),
                    row("Hợp đồng hiệu lực", report.effectiveCount()),
                    row("Hợp đồng sắp hết hạn", report.expiringCount())));
        });
    }

    public byte[] work(WorkStatisticsResponse report) {
        return write(book -> {
            book.filters("Thống kê công/nghỉ/tăng ca", List.of(
                    row("Từ ngày (fromDate)", report.fromDate().toString()),
                    row("Đến ngày (toDate, bao gồm)", report.toDate().toString()),
                    row("Công/vắng", "Đếm bản ghi đã xác nhận; một nhân viên có thể có nhiều bản ghi trong ngày."),
                    row("Giờ nghỉ phép", "Thời lượng trong đơn đã duyệt giao với kỳ; chưa quy đổi theo lịch làm việc."),
                    row("Giờ tăng ca", "Thời gian thực tế đã duyệt và xác nhận giao với kỳ; phân bổ phút nghỉ theo tỷ lệ thời lượng.")));
            book.table("Chi tieu", new String[]{"Chỉ tiêu", "Giá trị", "Đơn vị"}, List.of(
                    row("Bản ghi đi làm đã xác nhận", report.confirmedWorkDays(), "Bản ghi"),
                    row("Bản ghi vắng đã xác nhận", report.confirmedAbsentDays(), "Bản ghi"),
                    row("Đơn nghỉ phép đã duyệt", report.approvedLeaveRequests(), "Đơn"),
                    row("Thời lượng nghỉ phép đã duyệt", report.approvedLeaveHours(), "Giờ"),
                    row("Đơn tăng ca đã duyệt và xác nhận", report.approvedOvertimeRequests(), "Đơn"),
                    row("Giờ tăng ca sau phân bổ phút nghỉ", report.approvedOvertimeHours(), "Giờ")));
        });
    }

    public byte[] payroll(PayrollStatisticsResponse report) {
        return write(book -> {
            book.filters("Báo cáo quỹ lương", List.of(
                    row("Từ ngày (fromDate)", report.fromDate().toString()),
                    row("Đến ngày (toDate, bao gồm)", report.toDate().toString()),
                    row("Chọn kỳ", "Chỉ lấy kỳ nằm trọn trong khoảng lọc."),
                    row("Trạng thái và phiên bản", "Chỉ lấy batch DA_CHOT mới nhất của mỗi kỳ."),
                    row("Ngày cuối kỳ", "periodEndExclusive là mốc kết thúc không bao gồm trong kỳ."),
                    row("Phụ cấp và thưởng", "Đã nằm trong quỹ lương trước khấu trừ; không cộng thêm lần nữa."),
                    row("Số bản ghi lương", "Là tổng bản ghi qua các kỳ, không phải số nhân viên duy nhất.")));
            Amounts totals = report.totals();
            book.table("Tong hop", new String[]{"Chỉ tiêu", "Giá trị"}, List.of(
                    row("Số batch đã chốt", report.batchCount()),
                    row("Số bản ghi lương", report.payrollRecordCount()),
                    row("Quỹ lương trước khấu trừ", totals.grossPayroll()),
                    row("Phụ cấp (đã gồm trong quỹ lương)", totals.allowances()),
                    row("Thưởng (đã gồm trong quỹ lương)", totals.bonuses()),
                    row("Khấu trừ", totals.deductions()),
                    row("Thực nhận", totals.netPayroll())));
            List<Object[]> batches = new ArrayList<>();
            for (PayrollStatisticsResponse.BatchSummary batch : report.batches()) {
                Amounts amounts = batch.totals();
                batches.add(row(textId(batch.periodId()), batch.periodCode(), batch.periodStart().toString(),
                        batch.periodEndExclusive().toString(), textId(batch.batchId()), batch.versionNo(),
                        batch.employeeCount(), amounts.grossPayroll(), amounts.allowances(), amounts.bonuses(),
                        amounts.deductions(), amounts.netPayroll()));
            }
            book.table("Theo batch", new String[]{"ID kỳ", "Mã kỳ", "Ngày bắt đầu kỳ",
                    "Kết thúc kỳ (không bao gồm)", "ID batch", "Phiên bản", "Số bản ghi lương",
                    "Quỹ lương trước khấu trừ", "Phụ cấp (đã gồm)", "Thưởng (đã gồm)", "Khấu trừ", "Thực nhận"}, batches);
        });
    }

    private byte[] write(Consumer<Book> content) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            content.accept(new Book(workbook));
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Không thể tạo tệp Excel báo cáo", exception);
        }
    }

    private static Object[] row(Object... values) {
        return values;
    }

    private static String textId(Long id) {
        return id == null ? "" : id.toString();
    }

    private static final class Book {
        private final XSSFWorkbook workbook;
        private final CellStyle normal;
        private final CellStyle header;
        private final CellStyle decimal;
        private final CellStyle integer;

        private Book(XSSFWorkbook workbook) {
            this.workbook = workbook;
            normal = workbook.createCellStyle();
            normal.setWrapText(true);
            normal.setVerticalAlignment(VerticalAlignment.TOP);
            header = workbook.createCellStyle();
            header.cloneStyleFrom(normal);
            header.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Font font = workbook.createFont();
            font.setBold(true);
            font.setColor(IndexedColors.WHITE.getIndex());
            header.setFont(font);
            decimal = workbook.createCellStyle();
            decimal.cloneStyleFrom(normal);
            decimal.setDataFormat(workbook.createDataFormat().getFormat("#,##0.0000"));
            integer = workbook.createCellStyle();
            integer.cloneStyleFrom(normal);
            integer.setDataFormat(workbook.createDataFormat().getFormat("0"));
        }

        private void filters(String reportName, List<Object[]> filters) {
            List<Object[]> rows = new ArrayList<>();
            rows.add(row("Báo cáo", reportName));
            rows.addAll(filters);
            rows.add(row("Định dạng", "Ngày theo ISO yyyy-MM-dd. ID kỳ/batch và số vượt độ chính xác Excel được lưu dạng chữ."));
            rows.add(row("Độ chính xác", "Tiền và giờ hiển thị 4 chữ số thập phân; ô dạng chữ giữ nguyên giá trị thập phân."));
            Sheet sheet = table("Bo loc", new String[]{"Bộ lọc / quy tắc", "Giá trị áp dụng"}, rows);
            sheet.setColumnWidth(0, 48 * 256);
            sheet.setColumnWidth(1, 105 * 256);
            for (int index = 1; index <= rows.size(); index++) {
                sheet.getRow(index).setHeightInPoints(42);
            }
        }

        private void employeeGroups(String name, List<EmployeeStatisticsRow> groups) {
            List<Object[]> rows = new ArrayList<>();
            List<EmployeeStatisticsRow> sortedGroups = groups.stream()
                    .sorted(Comparator.comparing(EmployeeStatisticsRow::getGroupId,
                            Comparator.nullsLast(Comparator.naturalOrder())))
                    .toList();
            for (EmployeeStatisticsRow group : sortedGroups) {
                rows.add(row(group.getGroupId(), group.getGroupCode(), group.getGroupName(), group.getEmployeeCount()));
            }
            Sheet sheet = table(name, new String[]{"ID nhóm", "Mã nhóm", "Tên nhóm", "Số nhân viên"}, rows);
            sheet.setColumnWidth(0, 24 * 256);
            sheet.setColumnWidth(2, 48 * 256);
        }

        private Sheet table(String name, String[] headings, List<Object[]> values) {
            Sheet sheet = workbook.createSheet(name);
            Row first = sheet.createRow(0);
            first.setHeightInPoints(36);
            for (int column = 0; column < headings.length; column++) {
                Cell cell = first.createCell(column);
                cell.setCellStyle(header);
                cell.setCellValue(headings[column]);
                sheet.setColumnWidth(column, (column == 0 ? 44 : 28) * 256);
            }
            for (Object[] items : values) {
                Row target = sheet.createRow(sheet.getLastRowNum() + 1);
                target.setHeightInPoints(32);
                for (int column = 0; column < items.length; column++) {
                    setValue(target.createCell(column), items[column]);
                }
            }
            sheet.createFreezePane(0, 1);
            if (!values.isEmpty()) {
                sheet.setAutoFilter(new CellRangeAddress(0, sheet.getLastRowNum(), 0, headings.length - 1));
            }
            return sheet;
        }

        private void setValue(Cell cell, Object value) {
            cell.setCellStyle(normal);
            if (value == null) {
                return;
            }
            if (value instanceof BigDecimal amount) {
                // Excel numeric cells retain at most 15 significant decimal digits.
                if (amount.stripTrailingZeros().precision() <= 15 && Double.isFinite(amount.doubleValue())
                        && (amount.signum() == 0 || amount.doubleValue() != 0)) {
                    cell.setCellStyle(decimal);
                    cell.setCellValue(amount.doubleValue());
                } else {
                    cell.setCellValue(amount.toPlainString());
                }
            } else if (value instanceof Long || value instanceof Integer) {
                long count = ((Number) value).longValue();
                if (count >= -999_999_999_999_999L && count <= 999_999_999_999_999L) {
                    cell.setCellStyle(integer);
                    cell.setCellValue(count);
                } else {
                    cell.setCellValue(Long.toString(count));
                }
            } else {
                // Explicit string cells do not interpret leading =, +, -, or @ as formulas.
                cell.setCellValue(value.toString());
            }
        }
    }
}
