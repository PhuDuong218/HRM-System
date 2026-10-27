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
import com.hrm.model.Position;
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

    public PositionController(PositionService positionService, DepartmentHeadAccessService access,
            CatalogAuditService catalog) {
        this.positionService = positionService;
        this.access = access;
        this.catalog = catalog;
    }

    @GetMapping(params = {"!page", "!size", "!keyword", "!active"})
    @PreAuthorize("hasAnyRole('ADMIN','HR') and hasAuthority('POSITION_MANAGE')")
    public List<PositionDTO> listPositions(Principal principal) {
        actorAccountId(principal);
        return positionService.getAllPositions();
    }

    @GetMapping({"", "/search"})
    @PreAuthorize("hasAnyRole('ADMIN','HR') and hasAuthority('POSITION_MANAGE')")
    public Page<PositionDTO> searchPositions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean active,
            Principal principal) {
        actorAccountId(principal);
        return positionService.searchPositions(keyword, active, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','HR') and hasAuthority('POSITION_MANAGE')")
    public PositionDTO getPositionDetails(@PathVariable Long id, Principal principal) {
        actorAccountId(principal);
        return positionService.getPositionById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','HR') and hasAuthority('POSITION_MANAGE')")
    public PositionDTO createPosition(@Valid @RequestBody PositionRequestDTO requestDTO, Principal principal) {
        return catalog.createPosition(requestDTO, actorAccountId(principal));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','HR') and hasAuthority('POSITION_MANAGE')")
    public PositionDTO replacePosition(@PathVariable Long id,
            @Valid @RequestBody PositionRequestDTO requestDTO, Principal principal) {
        return catalog.updatePosition(id, requestDTO, actorAccountId(principal));
    }

    @DeleteMapping(value = "/{id}", produces = "text/plain;charset=UTF-8")
    @PreAuthorize("hasAnyRole('ADMIN','HR') and hasAuthority('POSITION_MANAGE')")
    public ResponseEntity<String> deactivatePosition(@PathVariable Long id, Principal principal) {
        catalog.deactivatePosition(id, actorAccountId(principal));
        return ResponseEntity.ok("Đã vô hiệu hóa chức vụ thành công!");
    }

    @PreAuthorize("hasAnyRole('ADMIN','HR') and hasAuthority('POSITION_MANAGE')")
    public List<PositionDTO> getAllPositions() {
        return positionService.getAllPositions();
    }

    @PreAuthorize("hasAnyRole('ADMIN','HR') and hasAuthority('POSITION_MANAGE')")
    public PositionDTO getPositionById(Long id) {
        return positionService.getPositionById(id);
    }

    @PreAuthorize("hasAnyRole('ADMIN','HR') and hasAuthority('POSITION_MANAGE')")
    public PositionDTO addPosition(Position position) {
        return catalog.createPosition(toRequest(position), currentActorAccountId());
    }

    @PreAuthorize("hasAnyRole('ADMIN','HR') and hasAuthority('POSITION_MANAGE')")
    public PositionDTO updatePosition(Long id, Position position) {
        return catalog.updatePosition(id, toRequest(position), currentActorAccountId());
    }

    @PreAuthorize("hasAnyRole('ADMIN','HR') and hasAuthority('POSITION_MANAGE')")
    public ResponseEntity<String> deletePosition(Long id) {
        return deactivatePosition(id, SecurityContextHolder.getContext().getAuthentication());
    }

    private PositionRequestDTO toRequest(Position position) {
        if (position == null) {
            return null;
        }
        PositionRequestDTO request = new PositionRequestDTO();
        request.setPositionCode(position.getPositionCode());
        request.setPositionName(position.getPositionName());
        request.setDescription(position.getDescription());
        request.setIsActive(position.getIsActive());
        return request;
    }

    private Long currentActorAccountId() {
        return actorAccountId(SecurityContextHolder.getContext().getAuthentication());
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
