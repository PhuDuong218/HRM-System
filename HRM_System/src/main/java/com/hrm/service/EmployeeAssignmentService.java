package com.hrm.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Qualifier;
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
                        throw new ResourceNotFoundException(
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
                                ea.getEffectiveTo());
        }

        @Transactional
        public EmployeeAssignmentDTO transferEmployee(
                        Long employeeId,
                        EmployeeTransferRequestDTO request) {

                guard.lockTimeline();
                LocalDate asOf = LocalDate.now(businessClock);

                // 1. Tìm và khóa nhân viên
                Employee employee = employeeRepository
                                .findByIdForUpdate(employeeId)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Không tìm thấy nhân viên: " + employeeId));

                // 2. Chỉ cho phép điều chuyển nhân viên đang làm việc
                if (employee.getEmploymentStatus() != EmploymentStatus.DANG_LAM) {
                        throw new ResponseStatusException(
                                        HttpStatus.CONFLICT,
                                        "Nhân viên không còn đang làm việc");
                }

                // 3. Kiểm tra phòng ban mới
                Department newDepartment = departmentRepository
                                .findById(request.departmentId())
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Phòng ban mới không tồn tại: "
                                                                + request.departmentId()));

                // 4. Kiểm tra chức vụ mới
                Position newPosition = positionRepository
                                .findById(request.positionId())
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Chức vụ mới không tồn tại: "
                                                                + request.positionId()));

                // Kiểm tra trạng thái phòng ban
                if (!Boolean.TRUE.equals(newDepartment.getIsActive())) {
                        throw new ResponseStatusException(
                                        HttpStatus.CONFLICT,
                                        "Phòng ban đã ngừng hoạt động");
                }

                // Kiểm tra trạng thái chức vụ
                if (!Boolean.TRUE.equals(newPosition.getIsActive())) {
                        throw new ResponseStatusException(
                                        HttpStatus.CONFLICT,
                                        "Chức vụ đã ngừng hoạt động");
                }

                // 5. Kiểm tra ngày điều chuyển
                LocalDate transferDate = request.effectiveFrom();

                if (transferDate == null) {
                        throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "Ngày điều chuyển không được để trống");
                }

                if (transferDate.isBefore(employee.getHireDate())) {
                        throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "Ngày điều chuyển không được trước ngày vào làm");
                }

                if (transferDate.isAfter(asOf)) {
                        throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "Chưa hỗ trợ điều chuyển trong tương lai");
                }

                // 6. Tìm phân công hiện tại
                List<EmployeeAssignment> currentList =
        assignmentRepository.findCurrentAssignments(employeeId, asOf);

                if (currentList.size() != 1) {
                        throw new ResponseStatusException(
                                        HttpStatus.CONFLICT,
                                        "Nhân viên phải có đúng một phân công hiện tại");
                }

                EmployeeAssignment oldAssignment = currentList.get(0);
                if (AssignmentGuardService.HEAD_CODE.equals(oldAssignment.getPosition().getPositionCode())
                                || AssignmentGuardService.HEAD_CODE.equals(newPosition.getPositionCode())) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT,
                                        "Hãy dùng nghiệp vụ bổ nhiệm/kết thúc trưởng phòng trước khi điều chuyển");
                }

                // 7. Kiểm tra ngày hiệu lực
                if (!transferDate.isAfter(oldAssignment.getEffectiveFrom())) {
                        throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "Ngày điều chuyển phải sau ngày bắt đầu phân công cũ");
                }

                // 8. Kiểm tra có thay đổi phòng ban/chức vụ hay không
                boolean sameDepartment = oldAssignment.getDepartment().getDepartmentId()
                                .equals(request.departmentId());

                boolean samePosition = oldAssignment.getPosition().getPositionId()
                                .equals(request.positionId());

                if (sameDepartment && samePosition) {
                        throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "Phòng ban và chức vụ mới không thay đổi");
                }

                // 9. Kết thúc phân công cũ
                oldAssignment.setEffectiveTo(transferDate);
                oldAssignment.setUpdatedAt(LocalDateTime.now(businessClock.withZone(java.time.ZoneOffset.UTC)));

                // 10. Tạo phân công mới
                EmployeeAssignment newAssignment = new EmployeeAssignment();

                newAssignment.setEmployee(employee);
                newAssignment.setDepartment(newDepartment);
                newAssignment.setPosition(newPosition);
                newAssignment.setEffectiveFrom(transferDate);
                newAssignment.setEffectiveTo(null);
                newAssignment.setCreatedAt(LocalDateTime.now(businessClock.withZone(java.time.ZoneOffset.UTC)));

                // 11. Lưu dữ liệu
                assignmentRepository.saveAndFlush(oldAssignment);

                EmployeeAssignment savedAssignment = assignmentRepository.save(newAssignment);

                // 12. Trả kết quả
                return convertToDTO(savedAssignment);
        }

        @Transactional(readOnly = true)
        public Page<EmployeeAssignmentDTO> getAssignmentsPage(
                        int page, int size) {

                return assignmentRepository.findAllPagedWithDetails(
                                PagingUtils.create(
                                                page,
                                                size,
                                                Sort.by(
                                                                Sort.Order.desc("effectiveFrom"),
                                                                Sort.Order.desc("assignmentId"))))
                                .map(this::convertToDTO);
        }

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
