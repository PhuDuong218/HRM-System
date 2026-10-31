
package com.hrm.controller;

import java.security.Principal;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.hrm.dto.PositionDTO;
import com.hrm.dto.PositionRequestDTO;
import com.hrm.security.AuthenticatedAccount;
import com.hrm.service.CatalogAuditService;
import com.hrm.service.DepartmentHeadAccessService;
import com.hrm.service.PositionService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/positions")
public class PositionController {

    private final PositionService positionService;
    private final DepartmentHeadAccessService access;
    private final CatalogAuditService catalog;

    public PositionController(
            PositionService positionService,
            DepartmentHeadAccessService access,
            CatalogAuditService catalog) {

        this.positionService = positionService;
        this.access = access;
        this.catalog = catalog;
    }

    // 1. Lay danh sach chuc vu
    @GetMapping(params = {
            "!page", "!size", "!keyword", "!active"
    })
    @PreAuthorize("hasAnyAuthority('position.view','POSITION_MANAGE')")
    public List<PositionDTO> getAllPositions() {
        return positionService.getAllPositions();
    }

    // 2. Tim kiem va phan trang
    @GetMapping({ "", "/search" })
    @PreAuthorize("hasAnyAuthority('position.view','POSITION_MANAGE')")
    public Page<PositionDTO> searchPositions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean active,
            Principal principal) {

        actorAccountId(principal);

        return positionService.searchPositions(
                keyword, active, page, size);
    }

    // 3. Xem chi tiet chuc vu
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('position.view','POSITION_MANAGE')")
    public PositionDTO getPositionById(
            @PathVariable Long id) {

        return positionService.getPositionById(id);
    }

    // 4. Tao chuc vu va ghi audit
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('position.create','POSITION_MANAGE')")
    public PositionDTO addPosition(
            @Valid @RequestBody PositionRequestDTO requestDTO) {

        return catalog.createPosition(
                requestDTO,
                currentActorAccountId());
    }

    // 5. Cap nhat chuc vu va ghi audit
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('position.update','POSITION_MANAGE')")
    public PositionDTO updatePosition(
            @PathVariable Long id,
            @Valid @RequestBody PositionRequestDTO requestDTO) {

        return catalog.updatePosition(
                id,
                requestDTO,
                currentActorAccountId());
    }

    // 6. Vo hieu hoa chuc vu va ghi audit
    @DeleteMapping(value = "/{id}", produces = "text/plain;charset=UTF-8")
    @PreAuthorize("hasAnyAuthority('position.delete','POSITION_MANAGE')")
    public ResponseEntity<String> deletePosition(
            @PathVariable Long id) {

        catalog.deactivatePosition(
                id,
                currentActorAccountId());

        return ResponseEntity.ok(
                "Đã vô hiệu hóa chức vụ thành công!");
    }

    // 7. Xac dinh tai khoan thuc hien thao tac
    private Long currentActorAccountId() {
        return actorAccountId(
                SecurityContextHolder
                        .getContext()
                        .getAuthentication());
    }

    private Long actorAccountId(Principal principal) {

        if (principal instanceof Authentication authentication
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof AuthenticatedAccount actor) {

            return actor.accountId();
        }

        return access.requireManager(principal);
    }
}
