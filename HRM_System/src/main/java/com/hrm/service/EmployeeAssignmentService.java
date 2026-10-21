
package com.hrm.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.hrm.dto.EmployeeAssignmentDTO;
import com.hrm.dto.EmployeeTransferRequestDTO;
import com.hrm.enums.EmploymentStatus;
import com.hrm.exception.ResourceNotFoundException;
import com.hrm.model.Department;
import com.hrm.model.Employee;
import com.hrm.model.EmployeeAssignment;
import com.hrm.model.Position;
import com.hrm.repository.DepartmentRepository;
import com.hrm.repository.EmployeeAssignmentRepository;
import com.hrm.repository.EmployeeRepository;
import com.hrm.repository.PositionRepository;

@Service
public class EmployeeAssignmentService {

    private final EmployeeAssignmentRepository assignmentRepository;
    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final PositionRepository positionRepository;

    public EmployeeAssignmentService(
            EmployeeAssignmentRepository assignmentRepository,
            EmployeeRepository employeeRepository,
            DepartmentRepository departmentRepository,
            PositionRepository positionRepository) {

        this.assignmentRepository = assignmentRepository;
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
        this.positionRepository = positionRepository;
    }

    // 1. Lấy toàn bộ danh sách phân công nhân viên
    @Transactional(readOnly = true)
    public List<EmployeeAssignmentDTO> getAllAssignments() {

        return assignmentRepository.findAllWithDetails()
                .stream()
                .map(this::convertToDTO)
                .toList();
    }

    // 2. Lấy lịch sử phân công của một nhân viên
    @Transactional(readOnly = true)
    public List<EmployeeAssignmentDTO> getEmployeeHistory(Long employeeId) {

        if (!employeeRepository.existsById(employeeId)) {
            throw new ResourceNotFoundException(
                    "Không tìm thấy nhân viên có ID: " + employeeId);
        }

        return assignmentRepository.findHistoryByEmployeeId(employeeId)
                .stream()
                .map(this::convertToDTO)
                .toList();
    }

    // 3. Chuyển EmployeeAssignment Entity sang DTO
    private EmployeeAssignmentDTO convertToDTO(EmployeeAssignment ea) {

        return new EmployeeAssignmentDTO(
                ea.getAssignmentId(),
                ea.getEmployee().getEmployeeId(),
                ea.getEmployee().getEmployeeCode(),
                ea.getEmployee().getFullName(),
                ea.getDepartment().getDepartmentId(),
                ea.getDepartment().getDepartmentName(),
                ea.getPosition().getPositionId(),
                ea.getPosition().getPositionName(),
                ea.getEffectiveFrom(),
                ea.getEffectiveTo()
        );
    }

    // 4. Điều chuyển nhân viên sang phòng ban/chức vụ mới
    @Transactional
    public EmployeeAssignmentDTO transferEmployee(
            Long employeeId,
            EmployeeTransferRequestDTO request) {

        // Bước 1: Tìm và khóa nhân viên
        Employee employee = employeeRepository
                .findByIdForUpdate(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy nhân viên: " + employeeId));

        // Bước 2: Chỉ cho phép điều chuyển nhân viên đang làm việc
        if (employee.getEmploymentStatus() != EmploymentStatus.DANG_LAM) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Nhân viên không còn đang làm việc");
        }

        // Bước 3: Kiểm tra phòng ban mới
        Department newDepartment = departmentRepository
                .findById(request.departmentId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Phòng ban mới không tồn tại: "
                                + request.departmentId()));

        // Bước 4: Kiểm tra chức vụ mới
        Position newPosition = positionRepository
                .findById(request.positionId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Chức vụ mới không tồn tại: "
                                + request.positionId()));

        // Bước 5: Kiểm tra ngày điều chuyển
        LocalDate transferDate = request.effectiveFrom();

        if (transferDate == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Ngày điều chuyển không được để trống");
        }

        if (transferDate.isAfter(LocalDate.now())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Chưa hỗ trợ điều chuyển trong tương lai");
        }

        // Bước 6: Tìm phân công hiện tại
        List<EmployeeAssignment> currentList =
                assignmentRepository.findCurrentAssignments(employeeId);

        if (currentList.size() != 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Nhân viên phải có đúng một phân công hiện tại");
        }

        EmployeeAssignment oldAssignment = currentList.get(0);

        // Bước 7: Kiểm tra ngày bắt đầu phân công mới
        if (!transferDate.isAfter(oldAssignment.getEffectiveFrom())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Ngày điều chuyển phải sau ngày bắt đầu phân công cũ");
        }

        // Bước 8: Kiểm tra phòng ban/chức vụ có thay đổi không
        boolean sameDepartment =
                oldAssignment.getDepartment().getDepartmentId()
                        .equals(request.departmentId());

        boolean samePosition =
                oldAssignment.getPosition().getPositionId()
                        .equals(request.positionId());

        if (sameDepartment && samePosition) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Phòng ban và chức vụ mới không thay đổi");
        }

        // Bước 9: Kết thúc phân công cũ
        oldAssignment.setEffectiveTo(transferDate.minusDays(1));
        oldAssignment.setUpdatedAt(LocalDateTime.now());

        // Bước 10: Tạo phân công mới
        EmployeeAssignment newAssignment = new EmployeeAssignment();

        newAssignment.setEmployee(employee);
        newAssignment.setDepartment(newDepartment);
        newAssignment.setPosition(newPosition);
        newAssignment.setEffectiveFrom(transferDate);
        newAssignment.setEffectiveTo(null);
        newAssignment.setCreatedAt(LocalDateTime.now());

        // Bước 11: Lưu phân công cũ
        assignmentRepository.saveAndFlush(oldAssignment);

        // Bước 12: Lưu phân công mới
        EmployeeAssignment savedAssignment =
                assignmentRepository.save(newAssignment);

        // Bước 13: Trả về DTO
        return convertToDTO(savedAssignment);
    }
}
