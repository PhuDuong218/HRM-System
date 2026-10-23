package com.hrm.controller;

import com.hrm.model.Position;
import com.hrm.service.PositionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.List;

@RestController
@RequestMapping("/positions") // Tất cả API bắt đầu bằng http://localhost:2005/positions
public class PositionController {

    @Autowired
    private PositionService positionService;

    // Khi có người gọi GET /positions -> Trả về danh sách
    @GetMapping
    @PreAuthorize("hasAuthority('position.view')")
    public List<Position> getAllPositions() {
        return positionService.getAllPositions();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('position.view')")
    public Position getPositionById(@PathVariable Long id) {
        return positionService.getPositionById(id);
    }

    // Khi có người gọi POST /positions (kèm cục JSON) -> Gửi cho Service thêm mới
    @PostMapping
    @PreAuthorize("hasAuthority('position.create')")
    public Position addPosition(@RequestBody Position position) {
        return positionService.addPosition(position);
    }

    // Khi có người gọi PUT /positions/1 (kèm cục JSON) -> Gửi cho Service sửa chức vụ có ID=1
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('position.update')")
    public Position updatePosition(@PathVariable Long id, @RequestBody Position position) {
        return positionService.updatePosition(id, position);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('position.delete')")
    public ResponseEntity<String> deletePosition(@PathVariable Long id) {
        positionService.deletePosition(id);
        return ResponseEntity.ok("Đã vô hiệu hóa chức vụ thành công!");
    }
}