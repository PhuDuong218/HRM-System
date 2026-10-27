package com.hrm.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.hrm.dto.EmployeeContactPatchDTO;
import com.hrm.dto.EmployeeDTO;
import com.hrm.dto.EmployeeDetailDTO;
import com.hrm.dto.EmployeeRequestDTO;
import com.hrm.dto.EmployeeUpdateRequestDTO;
import com.hrm.enums.EmploymentStatus;
import com.hrm.exception.ResourceNotFoundException;
import com.hrm.model.Department;
import com.hrm.model.Employee;
import com.hrm.model.EmployeeAssignment;
import com.hrm.model.Position;
import com.hrm.repository.AccountRepository;
import com.hrm.repository.DepartmentRepository;
import com.hrm.repository.EmployeeAssignmentRepository;
import com.hrm.repository.EmployeeRepository;
import com.hrm.repository.PositionRepository;
import com.hrm.security.AuthenticatedAccount;
import com.hrm.util.PagingUtils;

@Service
public class EmployeeService {

    private static final Comparator<EmployeeAssignment> ASSIGNMENT_ORDER =
            Comparator.comparing(EmployeeAssignment::getEffectiveFrom)
                    .thenComparing(EmployeeAssignment::getAssignmentId,
                            Comparator.nullsFirst(Comparator.naturalOrder()));

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final PositionRepository positionRepository;
    private final AccountRepository accountRepository;
    private final EmployeeAssignmentRepository assignmentRepository;
    private final AssignmentGuardService guard;
    private final Clock businessClock;

    public EmployeeService(
            EmployeeRepository employeeRepository,
            DepartmentRepository departmentRepository,
            PositionRepository positionRepository,
            AccountRepository accountRepository,
            EmployeeAssignmentRepository assignmentRepository,
            AssignmentGuardService guard,
            @Qualifier("hrmBusinessClock") Clock businessClock) {
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
        this.positionRepository = positionRepository;
        this.accountRepository = accountRepository;
        this.assignmentRepository = assignmentRepository;
        this.guard = guard;
        this.businessClock = businessClock;
    }

    private LocalDate businessToday() {
        return LocalDate.now(businessClock);
    }

    private LocalDateTime auditNow() {
        return LocalDateTime.now(businessClock.withZone(ZoneOffset.UTC));
    }

    @Transactional(readOnly = true)
    public List<EmployeeDTO> getAllEmployees(AuthenticatedAccount actor) {
        if ("EMPLOYEE".equals(actor.roleCode())) {
            throw new AccessDeniedException("EMPLOYEE không được xem danh sách nhân sự");
        }
        return getAllEmployees();
    }

    @Transactional(readOnly = true)
    public List<EmployeeDTO> getAllEmployees() {
        LocalDate asOfDate = businessToday();
        return employeeRepository.findAllWithAssignments().stream()
                .map(employee -> convertToDTO(employee, asOfDate))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public EmployeeDTO getEmployeeById(Long id, AuthenticatedAccount actor) {
        if ("EMPLOYEE".equals(actor.roleCode()) && !Objects.equals(actor.employeeId(), id)) {
            throw new AccessDeniedException("Không được truy cập hồ sơ của nhân viên khác");
        }
        Employee employee = employeeRepository.findByIdWithAssignments(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhân viên có mã: " + id));
        return convertToDTO(employee);
    }

    @Transactional(readOnly = true)
    public Page<EmployeeDTO> searchEmployees(
            String keyword,
            EmploymentStatus status,
            Long departmentId,
            Long positionId,
            int page,
            int size) {
        LocalDate asOf = businessToday();
        String searchKeyword = "%" + (keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT)) + "%";
        Pageable pageable = PagingUtils.create(page, size, Sort.by(Sort.Direction.DESC, "employeeId"));
        Page<Employee> employees = employeeRepository.searchEmployees(
                searchKeyword, status, departmentId, positionId, asOf, pageable);
        List<Long> employeeIds = employees.getContent().stream()
                .map(Employee::getEmployeeId)
                .toList();

        if (employeeIds.isEmpty()) {
            return employees.map(employee -> convertToSearchDTO(employee, null));
        }

        // Keep the same deterministic choice as detail/statistics for overlapping legacy assignments.
        Map<Long, EmployeeAssignment> assignments = assignmentRepository
                .findCurrentByEmployeeIds(employeeIds, asOf).stream()
                .collect(Collectors.toMap(
                        assignment -> assignment.getEmployee().getEmployeeId(),
                        assignment -> assignment,
                        (first, second) -> ASSIGNMENT_ORDER.compare(first, second) >= 0 ? first : second));

        return employees.map(employee -> convertToSearchDTO(employee, assignments.get(employee.getEmployeeId())));
    }

    @Transactional(readOnly = true)
    public Page<EmployeeDTO> getEmployeesPage(int page, int size) {
        return searchEmployees(null, null, null, null, page, size);
    }

    @Transactional(readOnly = true)
    public EmployeeDTO getEmployeeById(Long id) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhân viên có mã: " + id));
        return convertToDTO(employee);
    }

    @Transactional(readOnly = true)
    public EmployeeDetailDTO getEmployeeDetailById(Long id) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhân viên: " + id));
        EmployeeDetailDTO detail = new EmployeeDetailDTO();
        BeanUtils.copyProperties(convertToDTO(employee), detail);
        detail.setAddress(employee.getAddress());
        detail.setIdentityNumber(employee.getIdentityNumber());
        detail.setTerminationDate(employee.getTerminationDate());
        return detail;
    }

    @Transactional
    public EmployeeDTO addEmployee(EmployeeRequestDTO requestDTO) {
        validateEmployeeDates(requestDTO.getBirthDate(), requestDTO.getHireDate());
        validateHireDateForCreation(requestDTO.getHireDate());
        guard.lockTimeline();

        String employeeCode = requestDTO.getEmployeeCode().trim();
        if (employeeRepository.existsByEmployeeCode(employeeCode)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Mã nhân viên đã tồn tại: " + employeeCode);
        }

        Department department = departmentRepository.findById(requestDTO.getDepartmentId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy phòng ban có ID: " + requestDTO.getDepartmentId()));
        Position position = positionRepository.findById(requestDTO.getPositionId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy chức vụ có ID: " + requestDTO.getPositionId()));

        if (!Boolean.TRUE.equals(department.getIsActive())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Không thể thêm nhân viên vào phòng ban đã ngừng hoạt động");
        }
        if (!Boolean.TRUE.equals(position.getIsActive())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Không thể phân công chức vụ đã ngừng hoạt động");
        }
        if (AssignmentGuardService.HEAD_CODE.equals(position.getPositionCode())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Tạo nhân viên với chức vụ thường, sau đó dùng API bổ nhiệm trưởng phòng");
        }

        Employee employee = new Employee();
        employee.setEmployeeCode(employeeCode);
        employee.setFullName(requestDTO.getFullName().trim());
        employee.setBirthDate(requestDTO.getBirthDate());
        employee.setEmail(normalizeContactText(requestDTO.getEmail()));
        employee.setPhone(normalizeContactText(requestDTO.getPhone()));
        employee.setIdentityNumber(normalizeContactText(requestDTO.getIdentityNumber()));
        employee.setAddress(normalizeContactText(requestDTO.getAddress()));
        employee.setHireDate(requestDTO.getHireDate());
        employee.setEmploymentStatus(EmploymentStatus.DANG_LAM);
        employee.setCreatedAt(auditNow());

        EmployeeAssignment assignment = new EmployeeAssignment();
        assignment.setEmployee(employee);
        assignment.setDepartment(department);
        assignment.setPosition(position);
        assignment.setEffectiveFrom(requestDTO.getHireDate());
        assignment.setCreatedAt(auditNow());
        employee.getAssignments().add(assignment);

        return convertToDTO(employeeRepository.save(employee));
    }

    @Transactional
    public EmployeeDetailDTO patchEmployeeContact(Long id, EmployeeContactPatchDTO request) {
        Employee employee = employeeRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhân viên: " + id));
        boolean supplied = false;
        if (request.getEmail() != null) {
            employee.setEmail(normalizeContactText(request.getEmail()));
            supplied = true;
        }
        if (request.getPhone() != null) {
            employee.setPhone(normalizeContactText(request.getPhone()));
            supplied = true;
        }
        if (request.getAddress() != null) {
            employee.setAddress(normalizeContactText(request.getAddress()));
            supplied = true;
        }
        if (request.getIdentityNumber() != null) {
            employee.setIdentityNumber(normalizeContactText(request.getIdentityNumber()));
            supplied = true;
        }
        if (supplied) {
            employee.setUpdatedAt(auditNow());
            employeeRepository.saveAndFlush(employee);
        }
        return getEmployeeDetailById(id);
    }

    /** Keeps the original profile-update API without changing assignment or identity data. */
    @Transactional
    public EmployeeDTO updateEmployee(Long id, EmployeeRequestDTO requestDTO) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhân viên có mã: " + id));
        validateEmployeeDates(requestDTO.getBirthDate(), employee.getHireDate());
        employee.setFullName(requestDTO.getFullName().trim());
        employee.setBirthDate(requestDTO.getBirthDate());
        employee.setEmail(normalizeContactText(requestDTO.getEmail()));
        employee.setPhone(normalizeContactText(requestDTO.getPhone()));
        employee.setUpdatedAt(auditNow());
        return convertToDTO(employeeRepository.save(employee));
    }

    @Transactional
    public EmployeeDTO updateEmployee(Long id, EmployeeUpdateRequestDTO requestDTO) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhân viên: " + id));
        validateEmployeeDates(requestDTO.getBirthDate(), employee.getHireDate());
        employee.setFullName(requestDTO.getFullName().trim());
        employee.setBirthDate(requestDTO.getBirthDate());
        employee.setEmail(normalizeContactText(requestDTO.getEmail()));
        employee.setPhone(normalizeContactText(requestDTO.getPhone()));
        employee.setIdentityNumber(normalizeContactText(requestDTO.getIdentityNumber()));
        employee.setUpdatedAt(auditNow());
        return convertToDTO(employeeRepository.save(employee));
    }

    @Transactional
    public void deleteEmployee(Long id) {
        guard.lockTimeline();
        Employee employee = employeeRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhân viên có ID: " + id));
        if (employee.getEmploymentStatus() == EmploymentStatus.DA_NGHI) {
            // Legacy retired employees must also lose access when this action is repeated.
            accountRepository.deactivateAndRevokeByEmployeeId(id);
            return;
        }

        LocalDate terminationDate = businessToday();
        List<EmployeeAssignment> currentAssignments = assignmentRepository.findCurrentAssignments(id, terminationDate);
        if (currentAssignments.size() != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Nhân viên phải có đúng một phân công hiện tại");
        }
        EmployeeAssignment currentAssignment = currentAssignments.get(0);
        if (AssignmentGuardService.HEAD_CODE.equals(currentAssignment.getPosition().getPositionCode())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Hãy kết thúc/thay trưởng phòng trước khi xử lý nghỉ việc");
        }
        if (terminationDate.isBefore(currentAssignment.getEffectiveFrom())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ngày nghỉ việc không được trước ngày bắt đầu phân công");
        }

        currentAssignment.setEffectiveTo(terminationDate.plusDays(1));
        currentAssignment.setUpdatedAt(auditNow());
        employee.setEmploymentStatus(EmploymentStatus.DA_NGHI);
        employee.setTerminationDate(terminationDate);
        employee.setUpdatedAt(auditNow());
        assignmentRepository.save(currentAssignment);
        employeeRepository.save(employee);

        // Flush employment changes before revoking access in the same transaction.
        accountRepository.deactivateAndRevokeByEmployeeId(id);
    }

    private EmployeeDTO convertToDTO(Employee employee) {
        return convertToDTO(employee, businessToday());
    }

    private EmployeeDTO convertToDTO(Employee employee, LocalDate asOfDate) {
        EmployeeAssignment currentAssignment = employee.getAssignments() == null ? null
                : employee.getAssignments().stream()
                        .filter(assignment -> !assignment.getEffectiveFrom().isAfter(asOfDate)
                                && (assignment.getEffectiveTo() == null
                                        || assignment.getEffectiveTo().isAfter(asOfDate)))
                        .max(ASSIGNMENT_ORDER)
                        .orElse(null);
        return convertToSearchDTO(employee, currentAssignment);
    }

    private EmployeeDTO convertToSearchDTO(Employee employee, EmployeeAssignment assignment) {
        EmployeeDTO dto = new EmployeeDTO();
        dto.setEmployeeId(employee.getEmployeeId());
        dto.setEmployeeCode(employee.getEmployeeCode());
        dto.setFullName(employee.getFullName());
        dto.setBirthDate(employee.getBirthDate());
        dto.setEmail(employee.getEmail());
        dto.setPhone(employee.getPhone());
        dto.setHireDate(employee.getHireDate());
        if (employee.getEmploymentStatus() != null) {
            dto.setEmploymentStatus(employee.getEmploymentStatus().name());
        }
        if (assignment != null) {
            dto.setCurrentDepartmentId(assignment.getDepartment().getDepartmentId());
            dto.setCurrentDepartmentName(assignment.getDepartment().getDepartmentName());
            dto.setCurrentPositionId(assignment.getPosition().getPositionId());
            dto.setCurrentPositionName(assignment.getPosition().getPositionName());
        }
        return dto;
    }

    private String normalizeContactText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void validateEmployeeDates(LocalDate birthDate, LocalDate hireDate) {
        if (hireDate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ngày vào làm không được để trống");
        }
        if (birthDate != null && !birthDate.isBefore(hireDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ngày sinh phải trước ngày vào làm");
        }
    }

    private void validateHireDateForCreation(LocalDate hireDate) {
        if (hireDate.isAfter(businessToday())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Chưa hỗ trợ tạo nhân viên có ngày vào làm trong tương lai");
        }
    }
}
