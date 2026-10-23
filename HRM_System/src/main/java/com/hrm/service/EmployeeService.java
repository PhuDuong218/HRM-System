
package com.hrm.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
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
import com.hrm.security.AuthenticatedAccount;

@Service
public class EmployeeService {

        private final EmployeeRepository employeeRepository;
        private final DepartmentRepository departmentRepository;
        private final PositionRepository positionRepository;

        public EmployeeService(
                        EmployeeRepository employeeRepository,
                        DepartmentRepository departmentRepository,
                        PositionRepository positionRepository) {

                this.employeeRepository = employeeRepository;
                this.departmentRepository = departmentRepository;
                this.positionRepository = positionRepository;
        }

        // 1. Lấy danh sách tất cả nhân viên
        @Transactional(readOnly = true)
        public List<EmployeeDTO> getAllEmployees(AuthenticatedAccount actor) {

                // EMPLOYEE không được xem danh sách toàn bộ nhân viên
                if ("EMPLOYEE".equals(actor.roleCode())) {
                        throw new AccessDeniedException(
                                        "EMPLOYEE không được xem danh sách nhân sự");
                }

                return employeeRepository.findAllWithCurrentAssignments()
                                .stream()
                                .map(this::convertToDTO)
                                .collect(Collectors.toList());
        }

        // 2. Lấy thông tin nhân viên theo ID
        @Transactional(readOnly = true)
        public EmployeeDTO getEmployeeById(
                        Long id,
                        AuthenticatedAccount actor) {

                // EMPLOYEE chỉ được xem hồ sơ của chính mình
                if ("EMPLOYEE".equals(actor.roleCode())
                                && !actor.employeeId().equals(id)) {

                        throw new AccessDeniedException(
                                        "Không được truy cập hồ sơ của nhân viên khác");
                }

                Employee entity = employeeRepository.findById(id)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Không tìm thấy nhân viên có mã: " + id));

                return convertToDTO(entity);
        }

        // 3. Thêm nhân viên mới
        @Transactional
        public EmployeeDTO addEmployee(EmployeeRequestDTO requestDTO) {

                // Bước 1: Kiểm tra và chuẩn hóa mã nhân viên
                if (requestDTO.getEmployeeCode() == null
                                || requestDTO.getEmployeeCode().trim().isEmpty()) {

                        throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "Mã nhân viên không được để trống");
                }

                String employeeCode = requestDTO.getEmployeeCode().trim();

                // Bước 2: Kiểm tra mã nhân viên đã tồn tại hay chưa
                if (employeeRepository.existsByEmployeeCode(employeeCode)) {

                        throw new ResponseStatusException(
                                        HttpStatus.CONFLICT,
                                        "Mã nhân viên đã tồn tại: " + employeeCode);
                }

                // Bước 3: Kiểm tra phòng ban có tồn tại
                Department department = departmentRepository
                                .findById(requestDTO.getDepartmentId())
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Không tìm thấy phòng ban có ID: "
                                                                + requestDTO.getDepartmentId()));

                // Bước 4: Kiểm tra chức vụ có tồn tại
                Position position = positionRepository
                                .findById(requestDTO.getPositionId())
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Không tìm thấy chức vụ có ID: "
                                                                + requestDTO.getPositionId()));

                // Bước 5: Tạo nhân viên mới
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

                // Bước 6: Tạo phân công ban đầu cho nhân viên
                EmployeeAssignment assignment = new EmployeeAssignment();

                assignment.setEmployee(employee);
                assignment.setDepartment(department);
                assignment.setPosition(position);
                assignment.setEffectiveFrom(requestDTO.getHireDate());
                assignment.setCreatedAt(LocalDateTime.now());

                // Bước 7: Gắn phân công vào danh sách nhân viên
                employee.getAssignments().add(assignment);

                // Bước 8: Lưu nhân viên và phân công thông qua Cascade
                Employee savedEmployee = employeeRepository.save(employee);

                // Bước 9: Trả về DTO
                return convertToDTO(savedEmployee);
        }

        // 4. Chuyển Employee Entity sang EmployeeDTO
        private EmployeeDTO convertToDTO(Employee entity) {

                EmployeeDTO dto = new EmployeeDTO();

                dto.setEmployeeId(entity.getEmployeeId());
                dto.setEmployeeCode(entity.getEmployeeCode());
                dto.setFullName(entity.getFullName());
                dto.setBirthDate(entity.getBirthDate());
                dto.setEmail(entity.getEmail());
                dto.setPhone(entity.getPhone());
                dto.setHireDate(entity.getHireDate());

                dto.setEmploymentStatus(
                                entity.getEmploymentStatus().name());

                // Lấy phòng ban và chức vụ hiện tại
                if (entity.getAssignments() != null
                                && !entity.getAssignments().isEmpty()) {

                        for (EmployeeAssignment assignment : entity.getAssignments()) {

                                if (assignment.getEffectiveTo() == null) {

                                        dto.setCurrentDepartmentId(
                                                        assignment.getDepartment().getDepartmentId());

                                        dto.setCurrentDepartmentName(
                                                        assignment.getDepartment().getDepartmentName());

                                        dto.setCurrentPositionId(
                                                        assignment.getPosition().getPositionId());

                                        dto.setCurrentPositionName(
                                                        assignment.getPosition().getPositionName());

                                        break;
                                }
                        }
                }

                return dto;
        }

        // 5. Cập nhật thông tin nhân viên
        @Transactional
        public EmployeeDTO updateEmployee(
                        Long id,
                        EmployeeRequestDTO requestDTO) {

                Employee existingEmployee = employeeRepository.findById(id)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Không tìm thấy nhân viên có mã: " + id));

                existingEmployee.setFullName(requestDTO.getFullName());
                existingEmployee.setBirthDate(requestDTO.getBirthDate());
                existingEmployee.setEmail(requestDTO.getEmail());
                existingEmployee.setPhone(requestDTO.getPhone());
                existingEmployee.setIdentityNumber(
                                requestDTO.getIdentityNumber());

                existingEmployee.setUpdatedAt(LocalDateTime.now());

                Employee savedEmployee = employeeRepository.save(existingEmployee);

                return convertToDTO(savedEmployee);
        }

        // 6. Xóa mềm nhân viên
        @Transactional
        public void deleteEmployee(Long id) {

                Employee existingEmployee = employeeRepository.findById(id)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Không tìm thấy nhân viên có mã: " + id));

                existingEmployee.setEmploymentStatus(
                                EmploymentStatus.DA_NGHI);

                existingEmployee.setTerminationDate(LocalDate.now());
                existingEmployee.setUpdatedAt(LocalDateTime.now());

                employeeRepository.save(existingEmployee);
        }
}
