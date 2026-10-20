package com.hrm.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.hrm.dto.EmployeeDTO;
import com.hrm.dto.EmployeeRequestDTO;
import com.hrm.enums.EmploymentStatus;
import com.hrm.exception.ResourceNotFoundException;
import com.hrm.model.Department;
import com.hrm.model.Employee;
import com.hrm.model.EmployeeAssignment;
import com.hrm.model.Position;
import com.hrm.repository.DepartmentRepository;
import com.hrm.repository.EmployeeRepository;
import com.hrm.repository.PositionRepository;

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

    public List<EmployeeDTO> getAllEmployees() {
        // Dùng hàm Custom Query JOIN FETCH để gộp SQL, chống sập Database
        return employeeRepository.findAllWithCurrentAssignments().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public EmployeeDTO getEmployeeById(Long id) {
        Employee entity = employeeRepository.findById(id)
                .orElseThrow(()
                        -> new ResourceNotFoundException("Không tìm thấy nhân viên có mã: " + id)
                );
        return convertToDTO(entity);
    }

    @Transactional
    public EmployeeDTO addEmployee(EmployeeRequestDTO requestDTO) {

        // Chuẩn hóa mã nhân viên
        String employeeCode = requestDTO.getEmployeeCode().trim();

        // Kiểm tra mã nhân viên đã tồn tại hay chưa
        if (employeeRepository.existsByEmployeeCode(employeeCode)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Mã nhân viên đã tồn tại: " + employeeCode
            );
        }

        Department department = departmentRepository
                .findById(requestDTO.getDepartmentId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy phòng ban có ID: "
                        + requestDTO.getDepartmentId()));

        Position position = positionRepository
                .findById(requestDTO.getPositionId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy chức vụ có ID: "
                        + requestDTO.getPositionId()));

        // 3. Tạo nhân viên
        Employee employee = new Employee();

        employee.setEmployeeCode(employeeCode);
        employee.setFullName(requestDTO.getFullName());
        employee.setBirthDate(requestDTO.getBirthDate());
        employee.setEmail(requestDTO.getEmail());
        employee.setPhone(requestDTO.getPhone());
        employee.setIdentityNumber(requestDTO.getIdentityNumber());
        employee.setHireDate(requestDTO.getHireDate());
        employee.setEmploymentStatus(EmploymentStatus.DANG_LAM);
        employee.setCreatedAt(LocalDateTime.now());

        // 4. Tạo phân công
        EmployeeAssignment assignment = new EmployeeAssignment();

        assignment.setEmployee(employee);
        assignment.setDepartment(department);
        assignment.setPosition(position);
        assignment.setEffectiveFrom(requestDTO.getHireDate());
        assignment.setCreatedAt(LocalDateTime.now());

        // 5. Gắn phân công vào danh sách của nhân viên
        employee.getAssignments().add(assignment);

        // 6. Lưu nhân viên và phân công thông qua cascade
        Employee savedEmployee = employeeRepository.save(employee);

        // 7. Trả dữ liệu về client
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
                .orElseThrow(()
                        -> new ResourceNotFoundException("Không tìm thấy nhân viên có mã: " + id)
                );

        // Cập nhật các trường thông tin cơ bản từ requestDTO gửi lên
        existingEmployee.setFullName(requestDTO.getFullName());
        existingEmployee.setBirthDate(requestDTO.getBirthDate());
        existingEmployee.setEmail(requestDTO.getEmail());
        existingEmployee.setPhone(requestDTO.getPhone());
        existingEmployee.setIdentityNumber(requestDTO.getIdentityNumber());
        // Cập nhật Audit
        existingEmployee.setUpdatedAt(java.time.LocalDateTime.now());

        // Lưu xuống DB và gọi hàm gọt đẽo convertToDTO để trả về
        return convertToDTO(employeeRepository.save(existingEmployee));
    }

    @Transactional
    public void deleteEmployee(Long id) {
        Employee existingEmployee = employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhân viên có mã: " + id));

        // Xóa mềm: Chuyển trạng thái sang DA_NGHI và ghi nhận ngày nghỉ chính thức
        existingEmployee.setEmploymentStatus(EmploymentStatus.DA_NGHI);
        existingEmployee.setTerminationDate(LocalDate.now());
        existingEmployee.setUpdatedAt(LocalDateTime.now());

        // (Tùy chọn) Khóa luôn cả user account tương ứng nếu có
        employeeRepository.save(existingEmployee);
    }

}
