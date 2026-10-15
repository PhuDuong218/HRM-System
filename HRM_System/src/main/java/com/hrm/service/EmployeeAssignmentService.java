
package com.hrm.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hrm.dto.EmployeeAssignmentDTO;
import com.hrm.dto.EmployeeTransferRequestDTO;
import com.hrm.enums.EmploymentStatus;
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

    @Transactional(readOnly = true)
    public List<EmployeeAssignmentDTO> getAllAssignments() {
        return assignmentRepository.findAllWithDetails()
                .stream()
                .map(this::convertToDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EmployeeAssignmentDTO> getEmployeeHistory(Long employeeId) {

        if (!employeeRepository.existsById(employeeId)) {
            throw new RuntimeException(
                    "Không tìm thấy nhân viên có ID: " + employeeId);
        }

        return assignmentRepository.findHistoryByEmployeeId(employeeId)
                .stream()
                .map(this::convertToDTO)
                .toList();
    }

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

    
@Transactional
public EmployeeAssignmentDTO transferEmployee(
        Long employeeId,
        EmployeeTransferRequestDTO request) {

        System.out.println(">>> DA VAO TRANSFER EMPLOYEE: " + employeeId);
    // 1. Khóa và lấy nhân viên cần điều chuyển
    Employee employee = employeeRepository
            .findByIdForUpdate(employeeId)
            .orElseThrow(() ->
                    new IllegalArgumentException(
                            "Không tìm thấy nhân viên: " + employeeId));

    // 2. Chỉ điều chuyển nhân viên đang làm việc
    if (employee.getEmploymentStatus() != EmploymentStatus.DANG_LAM) {
        throw new IllegalArgumentException(
                "Nhân viên không còn đang làm việc");
    }

    // 3. Kiểm tra phòng ban mới
    Department newDepartment = departmentRepository
            .findById(request.departmentId())
            .orElseThrow(() ->
                    new IllegalArgumentException(
                            "Phòng ban mới không tồn tại"));

    // 4. Kiểm tra chức vụ mới
    Position newPosition = positionRepository
            .findById(request.positionId())
            .orElseThrow(() ->
                    new IllegalArgumentException(
                            "Chức vụ mới không tồn tại"));

    // 5. Kiểm tra ngày điều chuyển
    LocalDate transferDate = request.effectiveFrom();

    if (transferDate.isAfter(LocalDate.now())) {
        throw new IllegalArgumentException(
                "Bài thực hành chưa hỗ trợ điều chuyển tương lai");
    }

    // 6. Tìm phân công hiện tại
    List<EmployeeAssignment> currentList =
            assignmentRepository.findCurrentAssignments(employeeId);

    if (currentList.size() != 1) {
        throw new IllegalStateException(
                "Nhân viên phải có đúng một phân công hiện tại");
    }

    EmployeeAssignment oldAssignment = currentList.get(0);

    // 7. Ngày mới phải sau ngày bắt đầu phân công cũ
    if (!transferDate.isAfter(oldAssignment.getEffectiveFrom())) {
        throw new IllegalArgumentException(
                "Ngày điều chuyển phải sau ngày bắt đầu phân công cũ");
    }

    // 8. Kiểm tra có thực sự thay đổi phòng ban/chức vụ
    boolean sameDepartment =
            oldAssignment.getDepartment().getDepartmentId()
                    .equals(request.departmentId());

    boolean samePosition =
            oldAssignment.getPosition().getPositionId()
                    .equals(request.positionId());

    if (sameDepartment && samePosition) {
        throw new IllegalArgumentException(
                "Phòng ban và chức vụ mới không thay đổi");
    }

    // 9. Kết thúc phân công cũ
    oldAssignment.setEffectiveTo(transferDate.minusDays(1));
    oldAssignment.setUpdatedAt(LocalDateTime.now());

    // 10. Tạo phân công mới
    EmployeeAssignment newAssignment = new EmployeeAssignment();

    newAssignment.setEmployee(employee);
    newAssignment.setDepartment(newDepartment);
    newAssignment.setPosition(newPosition);
    newAssignment.setEffectiveFrom(transferDate);
    newAssignment.setEffectiveTo(null);
    newAssignment.setCreatedAt(LocalDateTime.now());

    // 11. Lưu vào database
    
assignmentRepository.saveAndFlush(oldAssignment);

EmployeeAssignment savedAssignment =
        assignmentRepository.save(newAssignment);


    // 12. Trả DTO
    return convertToDTO(savedAssignment);
}

}
