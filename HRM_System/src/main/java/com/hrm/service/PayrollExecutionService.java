package com.hrm.service;

import com.hrm.dto.AttendanceSummaryDTO;
import com.hrm.dto.EmployeeSalaryProfileDTO;
import com.hrm.enums.BatchStatus;
import com.hrm.model.PayrollBatch;
import com.hrm.model.PayrollDetail;
import com.hrm.model.PayrollLine;
import com.hrm.model.PayrollPeriod;
import com.hrm.model.PayrollSnapshot;
import com.hrm.repository.PayrollBatchRepository;
import com.hrm.repository.PayrollDetailRepository;
import com.hrm.repository.PayrollLineRepository;
import com.hrm.repository.PayrollPeriodRepository;
import com.hrm.repository.PayrollSnapshotRepository;
import com.hrm.service.engine.PayrollCalculationEngine;
import com.hrm.service.provider.AttendanceDataProvider;
import com.hrm.service.provider.SalaryContractProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class PayrollExecutionService {

    private final PayrollBatchRepository batchRepository;
    private final PayrollPeriodRepository periodRepository;
    private final PayrollDetailRepository detailRepository;
    private final PayrollLineRepository lineRepository;
    private final PayrollSnapshotRepository snapshotRepository;
    private final SalaryContractProvider salaryProvider;
    private final AttendanceDataProvider attendanceProvider;
    private final PayrollCalculationEngine calculationEngine;

    public PayrollExecutionService(PayrollBatchRepository batchRepository,
                                   PayrollPeriodRepository periodRepository,
                                   PayrollDetailRepository detailRepository,
                                   PayrollLineRepository lineRepository,
                                   PayrollSnapshotRepository snapshotRepository,
                                   SalaryContractProvider salaryProvider,
                                   AttendanceDataProvider attendanceProvider,
                                   PayrollCalculationEngine calculationEngine) {
        this.batchRepository = batchRepository;
        this.periodRepository = periodRepository;
        this.detailRepository = detailRepository;
        this.lineRepository = lineRepository;
        this.snapshotRepository = snapshotRepository;
        this.salaryProvider = salaryProvider;
        this.attendanceProvider = attendanceProvider;
        this.calculationEngine = calculationEngine;
    }

    /**
     * P-11: Chạy tính toán lương toàn bộ nhân viên cho một đợt lương nháp
     */
    @Transactional
    public String calculateBatch(Long batchId) {
        PayrollBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đợt bảng lương với ID: " + batchId));

        if (batch.getStatus() != BatchStatus.NHAP) {
            throw new RuntimeException("Bảng lương đã chốt hoặc công bố, không được phép tính lại trực tiếp!");
        }

        PayrollPeriod period = periodRepository.findById(batch.getPeriodId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy kỳ lương ID: " + batch.getPeriodId()));

        // 1. Xóa các dữ liệu tính toán cũ của đợt này nếu có (để tính lại sạch sẽ)
        List<PayrollDetail> oldDetails = detailRepository.findByBatchId(batchId);
        if (!oldDetails.isEmpty()) {
            List<Long> detailIds = new ArrayList<>();
            for (PayrollDetail d : oldDetails) {
                detailIds.add(d.getPayrollDetailId());
            }
            snapshotRepository.deleteByPayrollDetailIdIn(detailIds);
            lineRepository.deleteByPayrollDetailIdIn(detailIds);
            detailRepository.deleteAll(oldDetails);
        }

        // 2. Lấy danh sách nhân viên từ MockSalaryContractProvider (P-06)
        List<EmployeeSalaryProfileDTO> employeeProfiles = salaryProvider.getAllActiveSalaryProfiles(
                period.getPeriodStart(), period.getPeriodEnd());

        int count = 0;
        for (EmployeeSalaryProfileDTO profile : employeeProfiles) {
            // Lấy dữ liệu công từ MockAttendanceDataProvider (P-07)
            AttendanceSummaryDTO attendance = attendanceProvider.getAttendanceSummary(
                    profile.getEmployeeId(), period.getPeriodStart(), period.getPeriodEnd());

            // Lưu Header: payroll_details
            PayrollDetail detail = new PayrollDetail(batchId, profile.getEmployeeId());
            detail.setCreatedAt(LocalDateTime.now());
            detail = detailRepository.save(detail);

            // Tính các dòng tiền và lưu: payroll_lines (P-08, P-09, P-10)
            List<PayrollLine> lines = calculationEngine.calculatePayrollLines(
                    detail.getPayrollDetailId(), profile, attendance);
            for (PayrollLine line : lines) {
                line.setCreatedAt(LocalDateTime.now());
            }
            lineRepository.saveAll(lines);

            // Lưu Snapshot căn cứ đầu vào: payroll_snapshots (P-11)
            PayrollSnapshot snapshot = new PayrollSnapshot();
            snapshot.setPayrollDetailId(detail.getPayrollDetailId());
            snapshot.setSchemaVersion(1);
            snapshot.setPolicyVersion("2026.1");
            snapshot.setInputSnapshotJson(buildSnapshotJson(profile, attendance));
            snapshot.setCapturedAt(LocalDateTime.now());
            snapshot.setCreatedAt(LocalDateTime.now());
            snapshotRepository.save(snapshot);

            count++;
        }

        return "Tính lương thành công cho " + count + " nhân viên trong đợt bảng lương ID: " + batchId;
    }

    /**
     * Tự sinh chuỗi JSON chuẩn thuần Java để lưu vào cột NVARCHAR(MAX) của SQL Server
     */
    private String buildSnapshotJson(EmployeeSalaryProfileDTO profile, AttendanceSummaryDTO attendance) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"employeeId\":").append(profile.getEmployeeId()).append(",");
        sb.append("\"employeeCode\":\"").append(profile.getEmployeeCode() != null ? profile.getEmployeeCode() : "").append("\",");
        sb.append("\"fullName\":\"").append(profile.getFullName() != null ? profile.getFullName() : "").append("\",");
        sb.append("\"baseSalary\":").append(profile.getBaseSalary() != null ? profile.getBaseSalary().toPlainString() : "0").append(",");
        sb.append("\"standardWorkDays\":").append(attendance.getStandardWorkDays() != null ? attendance.getStandardWorkDays().toPlainString() : "0").append(",");
        sb.append("\"actualWorkDays\":").append(attendance.getActualWorkDays() != null ? attendance.getActualWorkDays().toPlainString() : "0").append(",");
        sb.append("\"overtimeHours\":").append(attendance.getOvertimeHours() != null ? attendance.getOvertimeHours().toPlainString() : "0").append(",");
        sb.append("\"paidLeaveDays\":").append(attendance.getPaidLeaveDays() != null ? attendance.getPaidLeaveDays().toPlainString() : "0").append(",");
        sb.append("\"calculatedAt\":\"").append(LocalDateTime.now()).append("\"");
        sb.append("}");
        return sb.toString();
    }
}