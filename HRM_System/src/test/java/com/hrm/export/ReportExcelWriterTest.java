package com.hrm.export;

import com.hrm.dto.EmployeeStatisticsResponse;
import com.hrm.repository.projection.EmployeeStatisticsRow;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReportExcelWriterTest {
    private final ReportExcelWriter writer = new ReportExcelWriter();

    @Test
    void exportsGroupsInNumericIdOrderWithUnassignedLastAndKeepsRowsTogether() throws Exception {
        List<EmployeeStatisticsRow> groups = List.of(
                group(null, "CHUA_PHAN_CONG", "Chưa có phân công hiệu lực", 3),
                group(2L, "DT", "Đào tạo", 16),
                group(10L, "GVU", "Giáo vụ", 6),
                group(1L, "HCNS", "Hành chính nhân sự", 1));
        var report = new EmployeeStatisticsResponse(groups, groups, List.of(), groups);

        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(writer.employees(report)))) {
            for (String name : List.of("Theo phong ban", "Theo chuc vu", "Theo trinh do")) {
                Sheet sheet = workbook.getSheet(name);
                assertGroup(sheet, 1, 1, "HCNS", "Hành chính nhân sự", 1);
                assertGroup(sheet, 2, 2, "DT", "Đào tạo", 16);
                assertGroup(sheet, 3, 10, "GVU", "Giáo vụ", 6);
                assertEquals(CellType.BLANK, sheet.getRow(4).getCell(0).getCellType());
                assertEquals("CHUA_PHAN_CONG", sheet.getRow(4).getCell(1).getStringCellValue());
                assertEquals(3, sheet.getRow(4).getCell(3).getNumericCellValue());
            }
            assertEquals(0, workbook.getSheet("Theo trang thai").getLastRowNum());
        }
        // Exporting must not reorder the report used by other consumers.
        assertNull(groups.getFirst().getGroupId());
        assertEquals(2L, groups.get(1).getGroupId());
    }

    @Test
    void preservesLargeIdsExactlyAndKeepsIdLessGroupsInTheirOriginalOrder() throws Exception {
        List<EmployeeStatisticsRow> groups = List.of(
                group(9_007_199_254_740_993L, "LARGE", "ID lớn", 1),
                group(999_999_999_999_999L, "SAFE", "ID số", 2));
        List<EmployeeStatisticsRow> statuses = List.of(
                group(null, "DA_NGHI", "Đã nghỉ", 3),
                group(null, "DANG_LAM", "Đang làm", 4));
        var report = new EmployeeStatisticsResponse(groups, List.of(), statuses, List.of());

        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(writer.employees(report)))) {
            Sheet sheet = workbook.getSheet("Theo phong ban");
            assertGroup(sheet, 1, 999_999_999_999_999L, "SAFE", "ID số", 2);
            assertEquals(CellType.STRING, sheet.getRow(2).getCell(0).getCellType());
            assertEquals("9007199254740993", sheet.getRow(2).getCell(0).getStringCellValue());
            Sheet statusSheet = workbook.getSheet("Theo trang thai");
            assertEquals("DA_NGHI", statusSheet.getRow(1).getCell(1).getStringCellValue());
            assertEquals("DANG_LAM", statusSheet.getRow(2).getCell(1).getStringCellValue());
        }
    }

    private static void assertGroup(Sheet sheet, int rowIndex, long id, String code, String name, long count) {
        var row = sheet.getRow(rowIndex);
        assertEquals(CellType.NUMERIC, row.getCell(0).getCellType());
        assertEquals((double) id, row.getCell(0).getNumericCellValue());
        assertEquals(code, row.getCell(1).getStringCellValue());
        assertEquals(name, row.getCell(2).getStringCellValue());
        assertEquals((double) count, row.getCell(3).getNumericCellValue());
    }

    private static EmployeeStatisticsRow group(Long id, String code, String name, long count) {
        return new EmployeeStatisticsRow() {
            @Override public Long getGroupId() { return id; }
            @Override public String getGroupCode() { return code; }
            @Override public String getGroupName() { return name; }
            @Override public Long getEmployeeCount() { return count; }
        };
    }
}
