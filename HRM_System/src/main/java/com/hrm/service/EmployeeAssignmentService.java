
package com.hrm.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
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
import com.hrm.util.PagingUtils;

@Service
public class EmployeeAssignmentService {
    @org.springframework.beans.factory.annotation.Autowired private PersonnelScopeService personnelScope;


        private final EmployeeAssignmentRepository assignmentRepository;
        private final EmployeeRepository employeeRepository;
        private final DepartmentRepository departmentRepository;
        private final PositionRepository positionRepository;
        private final AssignmentGuardService guard;
        private final Clock businessClock;

        public EmployeeAssignmentService(
                        EmployeeAssignmentRepository assignmentRepository,
                        EmployeeRepository employeeRepository,
                        DepartmentRepository departmentRepository,
                        PositionRepository positionRepository,
                        AssignmentGuardService guard,
                        @Qualifier("hrmBusinessClock") Clock businessClock) {

                this.assignmentRepository = assignmentRepository;
                this.employeeRepository = employeeRepository;
                this.departmentRepository = departmentRepository;
                this.positionRepository = positionRepository;
                this.guard = guard;
                this.businessClock = businessClock;
        }

        // 1. Lay toan bo danh sach phan cong
        @Transactional(readOnly = true)
        public List<EmployeeAssignmentDTO> getAllAssignments() {
                var allowed=PersonnelScopeService.isHr()?personnelScope.employees():null;
                return assignmentRepository.findAllWithDetails().stream().filter(a->allowed==null||allowed.contains(a.getEmployee().getEmployeeId())).toList()
                                .stream()
                                .map(this::convertToDTO)
                                .toList();
        }

        // 2. Lay lich su phan cong cua nhan vien
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

        // 3. Chuyen EmployeeAssignment thanh DTO
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
                                ea.getEffectiveTo());
        }

        // 4. Dieu chuyen nhan vien
        @Transactional
        public EmployeeAssignmentDTO transferEmployee(
                        Long employeeId,
                        EmployeeTransferRequestDTO request) {

                // Khoa timeline de tranh xu ly dong thoi
                guard.lockTimeline();

                LocalDate asOf = LocalDate.now(businessClock);

                // Tim va khoa nhan vien
                Employee employee = employeeRepository
                                .findByIdForUpdate(employeeId)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Không tìm thấy nhân viên: " + employeeId));

                // Chi dieu chuyen nhan vien dang lam viec
                if (employee.getEmploymentStatus() != EmploymentStatus.DANG_LAM) {
                        throw new ResponseStatusException(
                                        HttpStatus.CONFLICT,
                                        "Nhân viên không còn đang làm việc");
                }

                // Kiem tra phong ban moi
                Department newDepartment = departmentRepository
                                .findById(request.departmentId())
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Phòng ban mới không tồn tại: "
                                                                + request.departmentId()));

                // Kiem tra chuc vu moi
                Position newPosition = positionRepository
                                .findById(request.positionId())
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Chức vụ mới không tồn tại: "
                                                                + request.positionId()));

                if (!Boolean.TRUE.equals(newDepartment.getIsActive())) {
                        throw new ResponseStatusException(
                                        HttpStatus.CONFLICT,
                                        "Phòng ban đã ngừng hoạt động");
                }

                if (!Boolean.TRUE.equals(newPosition.getIsActive())) {
                        throw new ResponseStatusException(
                                        HttpStatus.CONFLICT,
                                        "Chức vụ đã ngừng hoạt động");
                }

                // Kiem tra ngay dieu chuyen
                LocalDate transferDate = request.effectiveFrom();

                if (transferDate == null) {
                        throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "Ngày điều chuyển không được để trống");
                }

                if (employee.getHireDate() != null
                                && transferDate.isBefore(employee.getHireDate())) {
                        throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "Ngày điều chuyển không được trước ngày vào làm");
                }

                if (transferDate.isAfter(asOf)) {
                        throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "Chưa hỗ trợ điều chuyển trong tương lai");
                }

                // Tim phan cong hien tai theo thoi diem
                List<EmployeeAssignment> currentList = assignmentRepository.findCurrentAssignments(
                                employeeId, asOf);

                if (currentList.size() != 1) {
                        throw new ResponseStatusException(
                                        HttpStatus.CONFLICT,
                                        "Nhân viên phải có đúng một phân công hiện tại");
                }

                EmployeeAssignment oldAssignment = currentList.get(0);

                // Truong phong phai theo nghiep vu rieng
                if (AssignmentGuardService.HEAD_CODE.equals(
                                oldAssignment.getPosition().getPositionCode())
                                || AssignmentGuardService.HEAD_CODE.equals(
                                                newPosition.getPositionCode())) {

                        throw new ResponseStatusException(
                                        HttpStatus.CONFLICT,
                                        "Hãy dùng nghiệp vụ bổ nhiệm/kết thúc trưởng phòng trước khi điều chuyển");
                }

                // Kiem tra ngay bat dau phan cong
                if (!transferDate.isAfter(oldAssignment.getEffectiveFrom())) {
                        throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "Ngày điều chuyển phải sau ngày bắt đầu phân công cũ");
                }

                // Kiem tra co thay doi phong ban/chuc vu
                boolean sameDepartment = oldAssignment.getDepartment().getDepartmentId()
                                .equals(request.departmentId());

                boolean samePosition = oldAssignment.getPosition().getPositionId()
                                .equals(request.positionId());

                if (sameDepartment && samePosition) {
                        throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "Phòng ban và chức vụ mới không thay đổi");
                }

                // Ket thuc phan cong cu
                // Quy uoc main: effectiveTo la ngay KHONG con hieu luc
                oldAssignment.setEffectiveTo(transferDate);
                oldAssignment.setUpdatedAt(
                                LocalDateTime.now(businessClock.withZone(ZoneOffset.UTC)));

                // Tao phan cong moi
                EmployeeAssignment newAssignment = new EmployeeAssignment();

                newAssignment.setEmployee(employee);
                newAssignment.setDepartment(newDepartment);
                newAssignment.setPosition(newPosition);
                newAssignment.setEffectiveFrom(transferDate);
                newAssignment.setEffectiveTo(null);
                newAssignment.setCreatedAt(
                                LocalDateTime.now(businessClock.withZone(ZoneOffset.UTC)));

                // Luu phan cong cu truoc
                assignmentRepository.saveAndFlush(oldAssignment);

                // Luu phan cong moi
                EmployeeAssignment savedAssignment = assignmentRepository.save(newAssignment);

                return convertToDTO(savedAssignment);
        }

        // 5. Phan trang danh sach phan cong
        @Transactional(readOnly = true)
        public Page<EmployeeAssignmentDTO> getAssignmentsPage(
                        int page,
                        int size) {

                if(PersonnelScopeService.isHr()) return assignmentRepository.findScoped(personnelScope.employees(),PagingUtils.create(page,size,Sort.by(Sort.Order.desc("effectiveFrom"),Sort.Order.desc("assignmentId")))).map(this::convertToDTO);
                return assignmentRepository.findAllPagedWithDetails(
                                PagingUtils.create(
                                                page,
                                                size,
                                                Sort.by(
                                                                Sort.Order.desc("effectiveFrom"),
                                                                Sort.Order.desc("assignmentId"))))
                                .map(this::convertToDTO);
        }

        // 6. Phan trang lich su phan cong
        @Transactional(readOnly = true)
        public Page<EmployeeAssignmentDTO> getHistoryPage(
                        Long employeeId,
                        int page,
                        int size) {

                if (!employeeRepository.existsById(employeeId)) {
                        throw new ResourceNotFoundException(
                                        "Không tìm thấy nhân viên: " + employeeId);
                }

                return assignmentRepository.findHistoryPagedByEmployeeId(
                                employeeId,
                                PagingUtils.create(
                                                page,
                                                size,
                                                Sort.by(
                                                                Sort.Order.desc("effectiveFrom"),
                                                                Sort.Order.desc("assignmentId"))))
                                .map(this::convertToDTO);
        }
}
