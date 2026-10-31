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

    public AdminAccessService(AccountRepository accounts, EmployeeRepository employees, RoleRepository roles,
            PermissionRepository permissions, AuditLogRepository audits, PasswordEncoder encoder) {
        this.accounts = accounts;
        this.employees = employees;
        this.roles = roles;
        this.permissions = permissions;
        this.audits = audits;
        this.encoder = encoder;
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
    public RoleView createRole(AuthenticatedAccount actor, CreateRole request) {
        adminWrite(actor);
        if (roles.existsByRoleCodeIgnoreCase(request.roleCode())) conflict("Mã vai trò đã tồn tại");
        Role role = new Role();
        role.setRoleCode(request.roleCode());
        role.setRoleName(request.roleName().trim());
        role.setPermissions(resolvePermissions(request.roleCode(), request.permissionIds()));
        roles.saveAndFlush(role);
        audit(actor, "ROLE_CREATED", "roles", role.getRoleId());
        return roleView(role);
    }

    @Transactional
    public RoleView updateRole(AuthenticatedAccount actor, long id, UpdateRole request) {
        adminWrite(actor);
        Role role = roleEntity(id);
        role.setRoleName(request.roleName().trim());
        roles.saveAndFlush(role);
        audit(actor, "ROLE_UPDATED", "roles", id);
        return roleView(role);
    }

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

    @Transactional
    public void deleteRole(AuthenticatedAccount actor, long id) {
        adminWrite(actor);
        Role role = roleEntity(id);
        if (SYSTEM_ROLES.contains(role.getRoleCode())) conflict("Không được xóa vai trò hệ thống");
        if (accounts.existsByRoleRoleId(id)) conflict("Vai trò đang được tài khoản sử dụng");
        roles.delete(role);
        roles.flush();
        audit(actor, "ROLE_DELETED", "roles", id);
    }

    public List<PermissionView> permissions() {
        return permissions.findAll(Sort.by("permissionId")).stream().map(this::permissionView).toList();
    }
    public PermissionView permission(long id) { return permissionView(permissionEntity(id)); }

    @Transactional
    public PermissionView createPermission(AuthenticatedAccount actor, CreatePermission request) {
        adminWrite(actor);
        if (request.permissionCode().toUpperCase(Locale.ROOT).startsWith("ROLE_"))
            throw new IllegalArgumentException("Tiền tố ROLE_ dành riêng cho vai trò xác thực");
        if (permissions.existsByPermissionCodeIgnoreCase(request.permissionCode())) conflict("Mã quyền đã tồn tại");
        Permission permission = new Permission();
        permission.setPermissionCode(request.permissionCode());
        permission.setPermissionName(request.permissionName().trim());
        permission.setDescription(request.description());
        permissions.saveAndFlush(permission);
        audit(actor, "PERMISSION_CREATED", "permissions", permission.getPermissionId());
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

    @Transactional
    public void deletePermission(AuthenticatedAccount actor, long id) {
        adminWrite(actor);
        Permission permission = permissionEntity(id);
        if ("ACCOUNT_MANAGE".equals(permission.getPermissionCode()) || roles.usesPermission(id))
            conflict("Không được xóa quyền quản trị hoặc quyền đang gán cho vai trò");
        permissions.delete(permission);
        permissions.flush();
        audit(actor, "PERMISSION_DELETED", "permissions", id);
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
