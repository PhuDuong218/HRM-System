package com.hrm.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.hrm.dto.EmployeeDetailDTO;
import org.springframework.beans.BeanUtils;
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
import com.hrm.repository.DepartmentRepository;
import com.hrm.repository.EmployeeAssignmentRepository;
import com.hrm.repository.EmployeeRepository;
import com.hrm.repository.PositionRepository;
import com.hrm.util.PagingUtils;
import com.hrm.dto.EmployeeContactPatchDTO;

@Service
public class EmployeeService {

        private final EmployeeRepository employeeRepository;
        private final DepartmentRepository departmentRepository;
        private final PositionRepository positionRepository;
        private final EmployeeAssignmentRepository assignmentRepository;
        private final AssignmentGuardService guard;
        private final Clock businessClock;

        public EmployeeService(
                        EmployeeRepository employeeRepository,
                        DepartmentRepository departmentRepository,
                        PositionRepository positionRepository,
                        EmployeeAssignmentRepository assignmentRepository,
                        AssignmentGuardService guard,
                        @Qualifier("hrmBusinessClock") Clock businessClock) {

                this.employeeRepository = employeeRepository;
                this.departmentRepository = departmentRepository;
                this.positionRepository = positionRepository;
                this.assignmentRepository = assignmentRepository;
                this.guard = guard;
                this.businessClock = businessClock;
        }

        private LocalDate businessToday() {
                return LocalDate.now(businessClock);
        }
        // public List<EmployeeDTO> getAllEmployees() {
        // // Dùng hàm Custom Query JOIN FETCH để gộp SQL, chống sập Database
        // return employeeRepository.findAllWithCurrentAssignments().stream()
        // .map(this::convertToDTO)
        // .collect(Collectors.toList());
        // }

        @Transactional(readOnly = true)
        public Page<EmployeeDTO> searchEmployees(
        String keyword,
        EmploymentStatus status,
        Long departmentId,
        Long positionId,
        int page,
        int size) {

    LocalDate asOf = businessToday();

                // 1. Kiểm tra tham số phân trang
                // if (page < 0 || size < 1 || size > 100) {
                // throw new ResponseStatusException(
                // HttpStatus.BAD_REQUEST,
                // "page >= 0 va size phai tu 1 den 100");
                // }

                // 2. Chuẩn hóa từ khóa
                String searchKeyword = keyword == null
                                ? ""
                                : keyword.trim().toLowerCase(Locale.ROOT);

                searchKeyword = "%" + searchKeyword + "%";

                // 3. Tạo thông tin phân trang

                Pageable pageable = PagingUtils.create(
                                page,
                                size,
                                Sort.by(
                                                Sort.Direction.DESC,
                                                "employeeId"));

                // 4. Tìm nhân viên
                Page<Employee> employees = employeeRepository.searchEmployees(
                                searchKeyword,
                                status,
                                departmentId,
                                positionId,
                                asOf,
                                pageable);

                // 5. Lấy ID của nhân viên trên trang hiện tại
                List<Long> employeeIds = employees.getContent()
                                .stream()
                                .map(Employee::getEmployeeId)
                                .toList();

                if (employeeIds.isEmpty()) {
                        return employees.map(e -> convertToSearchDTO(e, null));
                }

                // 6. Lấy phân công hiện tại cho cả trang
                Map<Long, EmployeeAssignment> assignments = assignmentRepository
                                .findCurrentByEmployeeIds(employeeIds, asOf)
                                .stream()
                                .collect(Collectors.toMap(
                                                a -> a.getEmployee().getEmployeeId(),
                                                a -> a,
                                                (first, second) -> first));

                // 7. Chuyển Entity thành DTO
                return employees.map(employee -> convertToSearchDTO(
                                employee,
                                assignments.get(employee.getEmployeeId())));
        }

        @Transactional(readOnly = true)
        public EmployeeDTO getEmployeeById(Long id) {
                Employee entity = employeeRepository.findById(id)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Không tìm thấy nhân viên có mã: " + id));

                return convertToDTO(entity);
        }

        @Transactional(readOnly = true)
        public EmployeeDetailDTO getEmployeeDetailById(Long id) {

                Employee employee = employeeRepository.findById(id)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Không tìm thấy nhân viên: " + id));

                EmployeeDTO basicInfo = convertToDTO(employee);

                EmployeeDetailDTO detail = new EmployeeDetailDTO();

                BeanUtils.copyProperties(basicInfo, detail);

                detail.setAddress(employee.getAddress());
                detail.setIdentityNumber(employee.getIdentityNumber());
                detail.setTerminationDate(employee.getTerminationDate());

                return detail;
        }

        @Transactional
        public EmployeeDTO addEmployee(EmployeeRequestDTO requestDTO) {

                validateEmployeeDates(
                                requestDTO.getBirthDate(),
                                requestDTO.getHireDate());

                validateHireDateForCreation(requestDTO.getHireDate());

                guard.lockTimeline();
                // Chuẩn hóa mã nhân viên
                String employeeCode = requestDTO.getEmployeeCode().trim();

                // Kiểm tra mã nhân viên đã tồn tại hay chưa
                if (employeeRepository.existsByEmployeeCode(employeeCode)) {
                        throw new ResponseStatusException(
                                        HttpStatus.CONFLICT,
                                        "Mã nhân viên đã tồn tại: " + employeeCode);
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

                // Kiểm tra phòng ban còn hoạt động
                if (!Boolean.TRUE.equals(department.getIsActive())) {

                        throw new ResponseStatusException(
                                        HttpStatus.CONFLICT,
                                        "Không thể thêm nhân viên vào phòng ban đã ngừng hoạt động");
                }

                // Kiểm tra chức vụ còn hoạt động
                if (!Boolean.TRUE.equals(position.getIsActive())) {

                        throw new ResponseStatusException(
                                        HttpStatus.CONFLICT,
                                        "Không thể phân công chức vụ đã ngừng hoạt động");
                }

                // 3. Tạo nhân viên
                if (AssignmentGuardService.HEAD_CODE.equals(position.getPositionCode())) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT,
                                        "Tạo nhân viên với chức vụ thường, sau đó dùng API bổ nhiệm trưởng phòng");
                }
                Employee employee = new Employee();

                employee.setEmployeeCode(employeeCode);
                employee.setFullName(requestDTO.getFullName().trim());
                employee.setBirthDate(requestDTO.getBirthDate());
                employee.setEmail(requestDTO.getEmail() == null ? null : normalizeContactText(requestDTO.getEmail()));
                employee.setPhone(requestDTO.getPhone() == null ? null : normalizeContactText(requestDTO.getPhone()));
                employee.setIdentityNumber(requestDTO.getIdentityNumber() == null ? null : normalizeContactText(requestDTO.getIdentityNumber()));
                employee.setHireDate(requestDTO.getHireDate());
                employee.setEmploymentStatus(EmploymentStatus.DANG_LAM);
                employee.setCreatedAt(LocalDateTime.now(businessClock.withZone(java.time.ZoneOffset.UTC)));
                String address = requestDTO.getAddress();

                employee.setAddress(
                                address == null || address.isBlank()
                                                ? null
                                                : address.trim());

                // 4. Tạo phân công
                EmployeeAssignment assignment = new EmployeeAssignment();

                assignment.setEmployee(employee);
                assignment.setDepartment(department);
                assignment.setPosition(position);
                assignment.setEffectiveFrom(requestDTO.getHireDate());
                assignment.setCreatedAt(LocalDateTime.now(businessClock.withZone(java.time.ZoneOffset.UTC)));

                // 5. Gắn phân công vào danh sách của nhân viên
                employee.getAssignments().add(assignment);

                // 6. Lưu nhân viên và phân công thông qua cascade
                Employee savedEmployee = employeeRepository.save(employee);

                // 7. Trả dữ liệu về client
                return convertToDTO(savedEmployee);
        }

        @Transactional
        public EmployeeDetailDTO patchEmployeeContact(
                        Long id,
                        EmployeeContactPatchDTO request) {

                Employee employee = employeeRepository.findByIdForUpdate(id)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Không tìm thấy nhân viên: " + id));

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
                        employee.setIdentityNumber(
                                        normalizeContactText(request.getIdentityNumber()));
                        supplied = true;
                }

                if (supplied) {
                        employee.setUpdatedAt(
                                        LocalDateTime.now(java.time.ZoneOffset.UTC));

                        employeeRepository.saveAndFlush(employee);
                }

                return getEmployeeDetailById(id);
        }

        private String normalizeContactText(String value) {
                return value.isBlank() ? null : value.trim();
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

                // Lấy phân công còn hiệu lực theo ngày nghiệp vụ Việt Nam.
                LocalDate today = businessToday();

                if (entity.getAssignments() != null && !entity.getAssignments().isEmpty()) {
                        for (EmployeeAssignment assignment : entity.getAssignments()) {
                                if (!assignment.getEffectiveFrom().isAfter(today)
                                                && (assignment.getEffectiveTo() == null
                                                                || today.isBefore(assignment.getEffectiveTo()))) {
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
        public EmployeeDTO updateEmployee(
                        Long id,
                        EmployeeUpdateRequestDTO requestDTO) {

                Employee employee = employeeRepository.findById(id)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Không tìm thấy nhân viên: " + id));
                validateEmployeeDates(
                                requestDTO.getBirthDate(),
                                employee.getHireDate());
                employee.setFullName(requestDTO.getFullName().trim());
                employee.setBirthDate(requestDTO.getBirthDate());
                employee.setEmail(requestDTO.getEmail() == null ? null : normalizeContactText(requestDTO.getEmail()));
                employee.setPhone(requestDTO.getPhone() == null ? null : normalizeContactText(requestDTO.getPhone()));
                employee.setIdentityNumber(requestDTO.getIdentityNumber() == null ? null : normalizeContactText(requestDTO.getIdentityNumber()));
                employee.setUpdatedAt(LocalDateTime.now(businessClock.withZone(java.time.ZoneOffset.UTC)));

                return convertToDTO(employeeRepository.save(employee));
        }

        @Transactional
        public void deleteEmployee(Long id) {

                guard.lockTimeline();

                // 1. Tìm và khóa nhân viên
                Employee employee = employeeRepository.findByIdForUpdate(id)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Không tìm thấy nhân viên có ID: " + id));

                // 2. Không cho nghỉ việc lần thứ hai
                if (employee.getEmploymentStatus() == EmploymentStatus.DA_NGHI) {
                        throw new ResponseStatusException(
                                        HttpStatus.CONFLICT,
                                        "Nhân viên đã nghỉ việc trước đó");
                }

                // 3. Lấy phân công hiện tại theo ngày nghiệp vụ Việt Nam
LocalDate asOf = businessToday();

List<EmployeeAssignment> currentAssignments =
        assignmentRepository.findCurrentAssignments(id, asOf);

if (currentAssignments.size() != 1) {
    throw new ResponseStatusException(
            HttpStatus.CONFLICT,
            "Nhân viên phải có đúng một phân công hiện tại");
}

LocalDate terminationDate = asOf;

                EmployeeAssignment currentAssignment = currentAssignments.get(0);
                if (AssignmentGuardService.HEAD_CODE.equals(currentAssignment.getPosition().getPositionCode())) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT,
                                        "Hãy kết thúc/thay trưởng phòng trước khi xử lý nghỉ việc");
                }

                // 4. Kiểm tra ngày nghỉ việc
                if (terminationDate.isBefore(currentAssignment.getEffectiveFrom())) {
                        throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "Ngày nghỉ việc không được trước ngày bắt đầu phân công");
                }

                // 5. Kết thúc phân công hiện tại
                currentAssignment.setEffectiveTo(terminationDate.plusDays(1));
                currentAssignment.setUpdatedAt(LocalDateTime.now(businessClock.withZone(java.time.ZoneOffset.UTC)));

                // 6. Cập nhật nhân viên thành đã nghỉ
                employee.setEmploymentStatus(EmploymentStatus.DA_NGHI);
                employee.setTerminationDate(terminationDate);
                employee.setUpdatedAt(LocalDateTime.now(businessClock.withZone(java.time.ZoneOffset.UTC)));

                // 7. Lưu các thay đổi
                assignmentRepository.save(currentAssignment);
                employeeRepository.save(employee);
        }

        private EmployeeDTO convertToSearchDTO(
                        Employee employee,
                        EmployeeAssignment assignment) {
                // LocalDate today = businessToday();
                EmployeeDTO dto = new EmployeeDTO();

                dto.setEmployeeId(employee.getEmployeeId());
                dto.setEmployeeCode(employee.getEmployeeCode());
                dto.setFullName(employee.getFullName());
                dto.setBirthDate(employee.getBirthDate());
                dto.setEmail(employee.getEmail());
                dto.setPhone(employee.getPhone());
                dto.setHireDate(employee.getHireDate());

                if (employee.getEmploymentStatus() != null) {
                        dto.setEmploymentStatus(
                                        employee.getEmploymentStatus().name());
                }

               if (assignment != null) {
                        dto.setCurrentDepartmentId(
                                        assignment.getDepartment().getDepartmentId());

                        dto.setCurrentDepartmentName(
                                        assignment.getDepartment().getDepartmentName());

                        dto.setCurrentPositionId(
                                        assignment.getPosition().getPositionId());

                        dto.setCurrentPositionName(
                                        assignment.getPosition().getPositionName());
                }

                return dto;
        }

        @Transactional(readOnly = true)
        public Page<EmployeeDTO> getEmployeesPage(int page, int size) {

                return searchEmployees(
                                null, // Không lọc tên hoặc mã
                                null, // Không lọc trạng thái
                                null, // Không lọc phòng ban
                                null, // Không lọc chức vụ
                                page,
                                size);
        }

        private void validateEmployeeDates(
                        LocalDate birthDate,
                        LocalDate hireDate) {

                if (hireDate == null) {
                        throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "Ngày vào làm không được để trống");
                }

                if (birthDate != null && !birthDate.isBefore(hireDate)) {
                        throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "Ngày sinh phải trước ngày vào làm");
                }
        }

        private void validateHireDateForCreation(LocalDate hireDate) {

                LocalDate today = businessToday();

                if (hireDate.isAfter(today)) {
                        throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "Chưa hỗ trợ tạo nhân viên có ngày vào làm trong tương lai");
                }
        }

}
