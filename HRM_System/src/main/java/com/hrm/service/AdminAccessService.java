package com.hrm.service;

import com.hrm.dto.AdminAccessDTOs.*;
import com.hrm.dto.PageResponse;
import com.hrm.enums.EmploymentStatus;
import com.hrm.exception.ResourceNotFoundException;
import com.hrm.model.*;
import com.hrm.repository.*;
import com.hrm.security.AuthenticatedAccount;
import com.hrm.util.PagingUtils;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
@PreAuthorize("hasRole('ADMIN') and hasAuthority('ACCOUNT_MANAGE')")
public class AdminAccessService {
    private static final Set<String> SYSTEM_ROLES = Set.of("ADMIN", "HR", "EMPLOYEE");
    private final AccountRepository accounts;
    private final EmployeeRepository employees;
    private final RoleRepository roles;
    private final PermissionRepository permissions;
    private final AuditLogRepository audits;
    private final PasswordEncoder encoder;
    private final HrDepartmentScopeService hrScopes;

    public AdminAccessService(AccountRepository accounts, EmployeeRepository employees, RoleRepository roles,
            PermissionRepository permissions, AuditLogRepository audits, PasswordEncoder encoder,
            HrDepartmentScopeService hrScopes) {
        this.accounts = accounts;
        this.employees = employees;
        this.roles = roles;
        this.permissions = permissions;
        this.audits = audits;
        this.encoder = encoder;
        this.hrScopes = hrScopes;
    }

    public PageResponse<AccountView> accounts(int page, int size, String keyword, Boolean active, Long roleId) {
        if (roleId != null && roleId < 1) throw new IllegalArgumentException("roleId phải lớn hơn 0");
        String term = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        if (term.length() > 100) throw new IllegalArgumentException("Từ khóa tối đa 100 ký tự");
        String pattern = term.isEmpty() ? "" : "%" + term.replace("!", "!!").replace("%", "!%")
                .replace("_", "!_").replace("[", "![") + "%";
        var result = accounts.search(pattern, active, roleId,
                PagingUtils.create(page, size, Sort.by("accountId")));
        // Fetch employee labels in one query for the whole page.
        Map<Long, Employee> labels = new HashMap<>();
        employees.findAllById(result.stream().map(Account::getEmployeeId).toList())
                .forEach(e -> labels.put(e.getEmployeeId(), e));
        return new PageResponse<>(result.stream().map(a -> view(a, labels.get(a.getEmployeeId()))).toList(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    public AccountView account(long id) { return view(accountEntity(id)); }

    public AccountContact accountContact(long id) {
        Account account = accountEntity(id);
        Employee employee = employees.findById(account.getEmployeeId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhân viên"));
        return new AccountContact(employee.getEmail(), employee.getPhone());
    }

    public PageResponse<EligibleEmployee> eligibleEmployees(String keyword, int page, int size) {
        String term = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        if (term.length() > 100) throw new IllegalArgumentException("Từ khóa tối đa 100 ký tự");
        String pattern = "%" + term.replace("!", "!!").replace("%", "!%")
                .replace("_", "!_").replace("[", "![") + "%";
        var result = employees.findWithoutAccount(pattern, EmploymentStatus.DANG_LAM,
                PagingUtils.create(page, size, Sort.by("employeeCode").and(Sort.by("employeeId"))));
        return new PageResponse<>(result.stream().map(e -> new EligibleEmployee(
                e.getEmployeeId(), e.getEmployeeCode(), e.getFullName())).toList(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public AccountView createAccount(AuthenticatedAccount actor, CreateAccount request) {
        adminWrite(actor);
        var employee = employees.findByIdForUpdate(request.employeeId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhân viên"));
        if (employee.getEmploymentStatus() != EmploymentStatus.DANG_LAM)
            throw new IllegalArgumentException("Chỉ tạo tài khoản cho nhân viên đang làm việc");
        if (accounts.existsByEmployeeId(request.employeeId())) conflict("Nhân viên đã có tài khoản");
        uniqueUsername(request.username(), 0L);
        if (!request.password().equals(request.confirmPassword()))
            throw new IllegalArgumentException("Mật khẩu và xác nhận mật khẩu không khớp");
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72)
            throw new IllegalArgumentException("Mật khẩu không được vượt quá 72 byte UTF-8");
        Account account = new Account();
        account.setEmployeeId(request.employeeId());
        account.setRole(roleEntity(request.roleId()));
        account.setUsername(request.username());
        account.setPasswordHash(encoder.encode(request.password()));
        account.setActive(request.active());
        var now = LocalDateTime.now(ZoneOffset.UTC);
        account.setCreatedAt(now);
        account.setUpdatedAt(now);
        account.setCreatedBy(actor.accountId());
        account.setUpdatedBy(actor.accountId());
        accounts.saveAndFlush(account);
        audit(actor, "ACCOUNT_CREATED", "accounts", account.getAccountId());
        return view(account, employee);
    }

    @Transactional
    public AccountView changeAccountStatus(AuthenticatedAccount actor, long id, ChangeAccountStatus request) {
        adminWrite(actor);
        Account account = accountEntity(id);
        if (account.isActive() != request.expectedActive()) conflict("Trạng thái tài khoản đã thay đổi. Vui lòng tải lại.");
        if (account.isActive() == request.active()) conflict("Tài khoản đã ở trạng thái yêu cầu.");
        if (actor.accountId().equals(id)) conflict("Không được tự khóa tài khoản đang sử dụng");
        if (request.active()) {
            var employee = employees.findByIdForUpdate(account.getEmployeeId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhân viên"));
            if (employee.getEmploymentStatus() != EmploymentStatus.DANG_LAM)
                conflict("Không thể mở tài khoản của nhân viên đã nghỉ việc");
        }
        account.setActive(request.active());
        account.revokeTokens();
        account.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        account.setUpdatedBy(actor.accountId());
        accounts.saveAndFlush(account);
        audit(actor, request.active() ? "ACCOUNT_UNLOCKED" : "ACCOUNT_LOCKED", "accounts", id);
        return view(account);
    }

    @Transactional
    public AccountView editAccountIdentity(AuthenticatedAccount actor, long id, EditAccountIdentity request) {
        adminWrite(actor);
        boolean scopeRequested = request.expectedDepartmentIds() != null || request.departmentIds() != null;
        if (scopeRequested) hrScopes.lockAccountForUpdate(actor, id);
        Account account = accountEntity(id);
        if (!account.getUsername().equals(request.expectedUsername()) ||
                !account.getRole().getRoleId().equals(request.expectedRoleId()))
            conflict("Thông tin tài khoản đã thay đổi. Vui lòng tải lại.");
        Role role = roleEntity(request.roleId());
        if (!SYSTEM_ROLES.contains(role.getRoleCode())) conflict("Vai trò không được hỗ trợ.");
        if (actor.accountId().equals(id) && !account.getRole().getRoleId().equals(request.roleId()))
            conflict("Không được thay đổi vai trò tài khoản đang sử dụng.");
        uniqueUsername(request.username(), id);
        boolean identityChanged = !account.getUsername().equals(request.username()) || !account.getRole().getRoleId().equals(request.roleId());
        if (scopeRequested && (!"HR".equals(role.getRoleCode()) || request.expectedDepartmentIds() == null || request.departmentIds() == null))
            throw new IllegalArgumentException("Phân công phòng ban chỉ áp dụng cho vai trò HR và cần đủ thông tin");
        if (!identityChanged && !scopeRequested)
            conflict("Thông tin tài khoản chưa thay đổi.");
        if (identityChanged) {
            account.setUsername(request.username());
            account.setRole(role);
            account.revokeTokens();
            account.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
            account.setUpdatedBy(actor.accountId());
            accounts.saveAndFlush(account);
        }
        if (scopeRequested) {
            hrScopes.replaceDepartments(actor, id, new com.hrm.dto.HrScopeDTOs.ReplaceDepartmentsRequest(
                    request.expectedDepartmentIds(), request.departmentIds(), "Quản trị viên cập nhật phạm vi khi sửa tài khoản"));
        }
        if (identityChanged) audit(actor, "ACCOUNT_UPDATED", "accounts", id);
        return view(account);
    }

    @Transactional
    public AccountView updateAccount(AuthenticatedAccount actor, long id, UpdateAccount request) {
        adminWrite(actor);
        Account account = accountEntity(id);
        Role role = roleEntity(request.roleId());
        if (actor.accountId().equals(id)
                && (!request.active() || !account.getRole().getRoleId().equals(request.roleId())))
            conflict("Không được tự khóa hoặc thay đổi vai trò tài khoản đang sử dụng");
        if (request.active()) {
            // Use the same employee lock as termination to prevent reactivation races.
            var employee = employees.findByIdForUpdate(account.getEmployeeId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhân viên"));
            if (employee.getEmploymentStatus() != EmploymentStatus.DANG_LAM)
                conflict("Không thể mở tài khoản của nhân viên đã nghỉ việc");
        }
        uniqueUsername(request.username(), id);
        boolean securityChanged = !account.getRole().getRoleId().equals(request.roleId())
                || account.isActive() != request.active() || !account.getUsername().equals(request.username());
        account.setRole(role);
        account.setUsername(request.username());
        account.setActive(request.active());
        if (securityChanged) account.revokeTokens();
        account.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        account.setUpdatedBy(actor.accountId());
        accounts.saveAndFlush(account);
        audit(actor, "ACCOUNT_UPDATED", "accounts", id);
        return view(account);
    }

    @Transactional
    public void deleteAccount(AuthenticatedAccount actor, long id) {
        adminWrite(actor);
        if (actor.accountId().equals(id)) conflict("Không được xóa tài khoản đang sử dụng");
        Account account = accountEntity(id);
        if (!account.isActive()) return;
        account.setActive(false);
        account.revokeTokens();
        account.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        account.setUpdatedBy(actor.accountId());
        accounts.saveAndFlush(account);
        audit(actor, "ACCOUNT_DEACTIVATED", "accounts", id);
    }

    public List<RoleView> roles() {
        return roles.findAll(Sort.by("roleId")).stream().map(this::roleView).toList();
    }
    public RoleView role(long id) { return roleView(roleEntity(id)); }

    @Transactional
    public RoleView replacePermissions(AuthenticatedAccount actor, long id, ReplacePermissions request) {
        adminWrite(actor);
        Role role = roleEntity(id);
        role.setPermissions(resolvePermissions(role.getRoleCode(), request.permissionIds()));
        roles.saveAndFlush(role);
        accounts.revokeRoleTokens(id, actor.accountId());
        audit(actor, "ROLE_PERMISSIONS_REPLACED", "roles", id);
        return roleView(role);
    }

    public List<PermissionView> permissions() {
        return permissions.findAll(Sort.by("permissionId")).stream().map(this::permissionView).toList();
    }
    public PermissionView permission(long id) { return permissionView(permissionEntity(id)); }

    @Transactional
    public PermissionView editPermissionDetails(AuthenticatedAccount actor, long id, EditPermissionDetails request) {
        adminWrite(actor);
        Permission permission = permissionEntity(id);
        if (!Objects.equals(permission.getPermissionName(), request.expectedName()) ||
                !Objects.equals(permission.getDescription(), request.expectedDescription()))
            conflict("Thông tin quyền đã thay đổi. Vui lòng tải lại.");
        String name = request.permissionName().trim();
        if (Objects.equals(permission.getPermissionName(), name) && Objects.equals(permission.getDescription(), request.description()))
            conflict("Thông tin quyền chưa thay đổi.");
        permission.setPermissionName(name);
        permission.setDescription(request.description());
        permissions.saveAndFlush(permission);
        audit(actor, "PERMISSION_UPDATED", "permissions", id);
        return permissionView(permission);
    }

    @Transactional
    public PermissionView updatePermission(AuthenticatedAccount actor, long id, UpdatePermission request) {
        adminWrite(actor);
        Permission permission = permissionEntity(id);
        permission.setPermissionName(request.permissionName().trim());
        permission.setDescription(request.description());
        permissions.saveAndFlush(permission);
        audit(actor, "PERMISSION_UPDATED", "permissions", id);
        return permissionView(permission);
    }

    private Set<Permission> resolvePermissions(String roleCode, Set<Long> ids) {
        Set<Permission> resolved = new HashSet<>(permissions.findAllById(ids));
        if (resolved.size() != ids.size()) throw new ResourceNotFoundException("Có quyền không tồn tại");
        if (resolved.stream().anyMatch(p -> p.getPermissionCode().toUpperCase(Locale.ROOT).startsWith("ROLE_")))
            throw new IllegalArgumentException("Không được cấp quyền có tiền tố ROLE_");
        boolean managesAccounts = resolved.stream().anyMatch(p -> "ACCOUNT_MANAGE".equals(p.getPermissionCode()));
        if ("ADMIN".equals(roleCode) && !managesAccounts) conflict("ADMIN phải giữ quyền ACCOUNT_MANAGE");
        if (!"ADMIN".equals(roleCode) && managesAccounts) conflict("Chỉ ADMIN được cấp quyền ACCOUNT_MANAGE");
        return resolved;
    }

    private void adminWrite(AuthenticatedAccount actor) {
        // All writes lock the same existing system role before reading mutable authorization state.
        roles.findByCodeForUpdate("ADMIN").orElseThrow(() -> new AccessDeniedException("Thiếu vai trò ADMIN"));
        Account current = actor == null ? null : accounts.findById(actor.accountId()).orElse(null);
        if (current == null || !current.isActive() || !"ADMIN".equals(current.getRole().getRoleCode())
                || !employees.existsByEmployeeIdAndEmploymentStatus(current.getEmployeeId(), EmploymentStatus.DANG_LAM)
                || !permissions.findCodesByRoleId(current.getRole().getRoleId()).contains("ACCOUNT_MANAGE"))
            throw new AccessDeniedException("Chỉ ADMIN đang hoạt động có quyền ACCOUNT_MANAGE được quản trị");
    }

    private void uniqueUsername(String username, long id) {
        if (accounts.existsByUsernameIgnoreCaseAndAccountIdNot(username, id)) conflict("Tên đăng nhập đã tồn tại");
    }
    private Account accountEntity(long id) {
        return accounts.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản"));
    }
    private Role roleEntity(long id) {
        return roles.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy vai trò"));
    }
    private Permission permissionEntity(long id) {
        return permissions.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy quyền"));
    }
    private AccountView view(Account a) { return view(a, employees.findById(a.getEmployeeId()).orElse(null)); }
    private AccountView view(Account a, Employee e) {
        return new AccountView(a.getAccountId(), a.getEmployeeId(), e == null ? null : e.getEmployeeCode(),
                e == null ? null : e.getFullName(), a.getRole().getRoleId(), a.getRole().getRoleCode(),
                a.getRole().getRoleName(), a.getUsername(), a.isActive(), a.getCreatedAt(), a.getUpdatedAt());
    }
    private PermissionView permissionView(Permission p) {
        return new PermissionView(p.getPermissionId(), p.getPermissionCode(), p.getPermissionName(), p.getDescription());
    }
    private RoleView roleView(Role r) {
        return new RoleView(r.getRoleId(), r.getRoleCode(), r.getRoleName(), r.getPermissions().stream()
                .sorted(Comparator.comparing(Permission::getPermissionId)).map(this::permissionView).toList());
    }
    private void audit(AuthenticatedAccount actor, String action, String table, Long id) {
        audits.save(new AuditLog(actor.accountId(), action, table, id, "Administrative access management"));
    }
    private void conflict(String message) { throw new ResponseStatusException(HttpStatus.CONFLICT, message); }
}
