package com.hrm.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.hrm.model.Employee;
import com.hrm.repository.EmployeeRepository;

import org.springframework.transaction.annotation.Transactional;

import com.hrm.dto.EmployeeDTO;
import com.hrm.dto.EmployeeRequestDTO;
import com.hrm.enums.EmploymentStatus;

import com.hrm.model.EmployeeAssignment;
import com.hrm.model.Department;
import com.hrm.model.Position;

import com.hrm.repository.DepartmentRepository;
import com.hrm.repository.PositionRepository;
import com.hrm.security.AuthenticatedAccount;
import org.springframework.security.access.AccessDeniedException;

@Service
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final PositionRepository positionRepository;

    public EmployeeService(EmployeeRepository employeeRepository,
            DepartmentRepository departmentRepository,
            PositionRepository positionRepository) {
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
        this.positionRepository = positionRepository;
    }

    public List<EmployeeDTO> getAllEmployees(AuthenticatedAccount actor) {
        if ("EMPLOYEE".equals(actor.roleCode())) {
            throw new AccessDeniedException("EMPLOYEE không được xem danh sách nhân sự");
        }
        // Dùng hàm Custom Query JOIN FETCH để gộp SQL, chống sập Database
        return employeeRepository.findAllWithCurrentAssignments().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public EmployeeDTO getEmployeeById(Long id, AuthenticatedAccount actor) {
        if ("EMPLOYEE".equals(actor.roleCode()) && !actor.employeeId().equals(id)) {
            throw new AccessDeniedException("Không được truy cập hồ sơ của nhân viên khác");
        }
        Employee entity = employeeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy nhân viên có mã: " + id));
        return convertToDTO(entity);
    }

    @Transactional
    public EmployeeDTO addEmployee(EmployeeRequestDTO requestDTO) {
        // 1. Kiểm tra xem Phòng ban và Chức vụ có tồn tại thật không?
        Department department = departmentRepository.findById(requestDTO.getDepartmentId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy Phòng ban!"));
        Position position = positionRepository.findById(requestDTO.getPositionId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy Chức vụ!"));

        // 2. Tạo Nhân viên (Lưu vào bảng employees)
        Employee newEmployee = new Employee();
        newEmployee.setEmployeeCode(requestDTO.getEmployeeCode());
        newEmployee.setFullName(requestDTO.getFullName());
        newEmployee.setBirthDate(requestDTO.getBirthDate());
        newEmployee.setEmail(requestDTO.getEmail());
        newEmployee.setPhone(requestDTO.getPhone());
        newEmployee.setHireDate(requestDTO.getHireDate());
        newEmployee.setEmploymentStatus(EmploymentStatus.DANG_LAM);
        newEmployee.setCreatedAt(LocalDateTime.now());

        // Lưu để lấy ID nhân viên vừa sinh ra
        Employee savedEmployee = employeeRepository.save(newEmployee);

        // 3. Tạo Phân công công tác (Lưu vào bảng employee_assignments)
        EmployeeAssignment assignment = new EmployeeAssignment();
        assignment.setEmployee(savedEmployee);
        assignment.setDepartment(department);
        assignment.setPosition(position);
        assignment.setEffectiveFrom(requestDTO.getHireDate());
        // effective_to để NULL mặc định vì đang đương nhiệm
        assignment.setCreatedAt(LocalDateTime.now());

        // Móc Assignment vào Employee
        savedEmployee.getAssignments().add(assignment);

        // TODO: Chuyển đổi savedEmployee thành EmployeeDTO để trả về (Bạn tự viết hàm này nhé)
        return convertToDTO(savedEmployee);
    }

    private EmployeeDTO convertToDTO(Employee entity) {
        EmployeeDTO dto = new EmployeeDTO();
        dto.setEmployeeId(entity.getEmployeeId());
        dto.setEmployeeCode(entity.getEmployeeCode());
        dto.setFullName(entity.getFullName());
        dto.setBirthDate(entity.getBirthDate());
        dto.setEmail(entity.getEmail());
        dto.setPhone(entity.getPhone());
        dto.setHireDate(entity.getHireDate());
        dto.setEmploymentStatus(entity.getEmploymentStatus().name()); // Chuyển Enum thành String cho Front-end

// Trích xuất Phòng ban và Chức vụ hiện tại (effective_to == null)
        if (entity.getAssignments() != null && !entity.getAssignments().isEmpty()) {
            for (EmployeeAssignment assignment : entity.getAssignments()) {
                if (assignment.getEffectiveTo() == null) { // Đang đương nhiệm
                    dto.setCurrentDepartmentId(assignment.getDepartment().getDepartmentId());
                    dto.setCurrentDepartmentName(assignment.getDepartment().getDepartmentName());
                    dto.setCurrentPositionId(assignment.getPosition().getPositionId());
                    dto.setCurrentPositionName(assignment.getPosition().getPositionName());
                    break; // Tìm thấy thì dừng vòng lặp
                }
            }
        }
        return dto;
    }

    @Transactional
    public EmployeeDTO updateEmployee(Long id, EmployeeRequestDTO requestDTO) {
        Employee existingEmployee = employeeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy nhân viên có mã: " + id));

        // Cập nhật các trường thông tin cơ bản từ requestDTO gửi lên
        existingEmployee.setFullName(requestDTO.getFullName());
        existingEmployee.setBirthDate(requestDTO.getBirthDate());
        existingEmployee.setEmail(requestDTO.getEmail());
        existingEmployee.setPhone(requestDTO.getPhone());
        
        // Cập nhật Audit
            existingEmployee.setUpdatedAt(java.time.LocalDateTime.now());

        // Lưu xuống DB và gọi hàm gọt đẽo convertToDTO để trả về
        return convertToDTO(employeeRepository.save(existingEmployee));
    }

    @Transactional
    public void deleteEmployee(Long id) {
            Employee existingEmployee = employeeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy nhân viên có mã: " + id));
        
        
        // Xóa mềm: Chuyển trạng thái sang DA_NGHI và ghi nhận ngày nghỉ chính thức
        existingEmployee.setEmploymentStatus(EmploymentStatus.DA_NGHI);
        existingEmployee.setTerminationDate(LocalDate.now());
        existingEmployee.setUpdatedAt(LocalDateTime.now());

        // (Tùy chọn) Khóa luôn cả user account tương ứng nếu có
        employeeRepository.save(existingEmployee);
    }

}
