
package com.hrm.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.hrm.dto.LeaveEntitlementDTO;
import com.hrm.dto.LeaveEntitlementRequestDTO;
import com.hrm.enums.EmploymentStatus;
import com.hrm.exception.ResourceNotFoundException;
import com.hrm.model.Employee;
import com.hrm.model.LeaveEntitlement;
import com.hrm.model.LeaveType;
import com.hrm.repository.EmployeeRepository;
import com.hrm.repository.LeaveEntitlementRepository;
import com.hrm.repository.LeaveTypeRepository;
import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
@Service
public class LeaveEntitlementService {
    @org.springframework.beans.factory.annotation.Autowired private PersonnelScopeService personnelScope;


    private final LeaveEntitlementRepository entitlementRepository;
    private final EmployeeRepository employeeRepository;
    private final LeaveTypeRepository leaveTypeRepository;

    public LeaveEntitlementService(
            LeaveEntitlementRepository entitlementRepository,
            EmployeeRepository employeeRepository,
            LeaveTypeRepository leaveTypeRepository) {

        this.entitlementRepository = entitlementRepository;
        this.employeeRepository = employeeRepository;
        this.leaveTypeRepository = leaveTypeRepository;
    }

    
@Transactional(readOnly = true)
public Page<LeaveEntitlementDTO> getAllEntitlements(
        Long employeeId,
        Long leaveTypeId,
        LocalDate fromDate,
        LocalDate toDate,
        int page,
        int size) {

    // 1. Kiểm tra tham số phân trang
    if (page < 0 || size < 1 || size > 100) {
        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "page phải >= 0 và size phải từ 1 đến 100"
        );
    }

    // 2. Kiểm tra khoảng thời gian tìm kiếm
    if (fromDate != null && toDate != null
            && !fromDate.isBefore(toDate)) {
        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "fromDate phải trước toDate"
        );
    }

    // 3. Sắp xếp kỳ mới nhất trước
    Sort sort = Sort.by(
            Sort.Order.desc("periodStart"),
            Sort.Order.desc("entitlementId")
    );

    // 4. Tạo thông tin phân trang
    PageRequest pageable = PageRequest.of(page, size, sort);

    // 5. Truy vấn Repository và chuyển Entity sang DTO
    if(PersonnelScopeService.isHr()) return entitlementRepository.searchEntitlementsScoped(employeeId,leaveTypeId,fromDate,toDate,personnelScope.employees(),pageable).map(this::toDTO);
    return entitlementRepository.searchEntitlements(
            employeeId,
            leaveTypeId,
            fromDate,
            toDate,
            pageable
    ).map(this::toDTO);
}


    // 1. Lấy danh sách hạn mức theo nhân viên
    @Transactional(readOnly = true)
    public List<LeaveEntitlementDTO> getByEmployeeId(Long employeeId) {

        if (!employeeRepository.existsById(employeeId)) {
            throw new ResourceNotFoundException(
                    "Không tìm thấy nhân viên: " + employeeId
            );
        }

        return entitlementRepository.findByEmployeeId(employeeId)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    // 2. Lấy chi tiết hạn mức
    @Transactional(readOnly = true)
    public LeaveEntitlementDTO getById(Long id) {
        return toDTO(findRequired(id));
    }

    public List<LeaveEntitlementDTO> getAll() {
        return entitlementRepository.findAll()
                .stream()
                .map(this::toDTO)
                .toList();
    }

    // 3. Cấp hạn mức nghỉ mới
    @Transactional
    public LeaveEntitlementDTO addLeaveEntitlement(
            LeaveEntitlementRequestDTO request) {

        validatePeriod(request);

        Employee employee = requiredLockedEmployee(
                request.getEmployeeId()
        );

        if (employee.getEmploymentStatus() != EmploymentStatus.DANG_LAM) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Không thể cấp hạn mức cho nhân viên đã nghỉ việc"
            );
        }

        LeaveType leaveType = requiredActiveLeaveType(
                request.getLeaveTypeId()
        );

        // Kiểm tra trùng khoảng thời gian
        boolean overlapping = entitlementRepository.existsOverlappingPeriod(
                request.getEmployeeId(),
                request.getLeaveTypeId(),
                request.getPeriodStart(),
                request.getPeriodEnd()
        );

        if (overlapping) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Hạn mức nghỉ bị trùng khoảng thời gian"
            );
        }

        LeaveEntitlement entitlement = new LeaveEntitlement();

        entitlement.setEmployee(employee);
        entitlement.setLeaveType(leaveType);
        entitlement.setPeriodStart(request.getPeriodStart());
        entitlement.setPeriodEnd(request.getPeriodEnd());
        entitlement.setEntitledHours(request.getEntitledHours());
        entitlement.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));

        return toDTO(entitlementRepository.saveAndFlush(entitlement));
    }

    // 4. Cập nhật hạn mức nghỉ
    @Transactional
    public LeaveEntitlementDTO updateLeaveEntitlement(
            Long id,
            LeaveEntitlementRequestDTO request) {

        validatePeriod(request);

        LeaveEntitlement entitlement = findRequired(id);

        Long employeeId = entitlement.getEmployee().getEmployeeId();
        requiredLockedEmployee(employeeId);

        // Không cho chuyển hạn mức sang nhân viên khác
        if (!employeeId.equals(request.getEmployeeId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Không được thay đổi nhân viên của hạn mức"
            );
        }

        // Không cho đổi loại nghỉ của bản ghi lịch sử
        if (!entitlement.getLeaveType().getLeaveTypeId()
                .equals(request.getLeaveTypeId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Không được thay đổi loại nghỉ của hạn mức"
            );
        }

        // Kiểm tra các hạn mức khác, bỏ qua chính bản ghi đang sửa
        boolean overlapping = entitlementRepository
                .findByEmployeeId(employeeId)
                .stream()
                .filter(other ->
                        !other.getEntitlementId().equals(id)
                        && other.getLeaveType().getLeaveTypeId()
                                .equals(request.getLeaveTypeId())
                )
                .anyMatch(other ->
                        other.getPeriodStart().isBefore(request.getPeriodEnd())
                        && other.getPeriodEnd().isAfter(request.getPeriodStart())
                );

        if (overlapping) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Khoảng thời gian cập nhật bị trùng hạn mức khác"
            );
        }

        entitlement.setPeriodStart(request.getPeriodStart());
        entitlement.setPeriodEnd(request.getPeriodEnd());
        entitlement.setEntitledHours(request.getEntitledHours());
        entitlement.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));

        return toDTO(entitlementRepository.saveAndFlush(entitlement));
    }

    // 5. Kiểm tra ngày bắt đầu và kết thúc
    private void validatePeriod(LeaveEntitlementRequestDTO request) {

        if (!request.getPeriodStart().isBefore(request.getPeriodEnd())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Ngày bắt đầu phải trước ngày kết thúc kỳ"
            );
        }
    }

    // 6. Tìm và khóa nhân viên
    private Employee requiredLockedEmployee(Long employeeId) {

        return employeeRepository.findByIdForUpdate(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy nhân viên: " + employeeId
                ));
    }

    // 7. Kiểm tra loại nghỉ còn hoạt động
    private LeaveType requiredActiveLeaveType(Long leaveTypeId) {

        LeaveType leaveType = leaveTypeRepository.findById(leaveTypeId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy loại nghỉ: " + leaveTypeId
                ));

        if (!Boolean.TRUE.equals(leaveType.getIsActive())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Loại nghỉ đã ngừng hoạt động"
            );
        }

        return leaveType;
    }

    // 8. Tìm hạn mức theo ID
    private LeaveEntitlement findRequired(Long id) {

        return entitlementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy hạn mức nghỉ: " + id
                ));
    }

    // 9. Chuyển Entity sang DTO
    private LeaveEntitlementDTO toDTO(LeaveEntitlement entitlement) {

        return new LeaveEntitlementDTO(
                entitlement.getEntitlementId(),
                entitlement.getEmployee().getEmployeeId(),
                entitlement.getEmployee().getFullName(),
                entitlement.getLeaveType().getLeaveTypeId(),
                entitlement.getLeaveType().getTypeName(),
                entitlement.getPeriodStart(),
                entitlement.getPeriodEnd(),
                entitlement.getEntitledHours()
        );
    }
}
