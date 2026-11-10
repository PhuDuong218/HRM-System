package com.hrm.controller;
import com.hrm.service.NotificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import java.security.Principal;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
 private final NotificationService service;
 public NotificationController(NotificationService service){this.service=service;}
 @GetMapping public NotificationService.Inbox list(@RequestParam(required=false) Boolean read,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int size,@RequestParam(required=false) Long snapshotId,Principal p){return service.list(read,page,size,snapshotId,p);}
 @GetMapping("/unread-count") public Map<String,Long> count(Principal p){return service.unread(p);}
 @PutMapping("/{id}/read") public void read(@PathVariable long id,Principal p){service.read(id,p);}
 public record ReadAll(@PositiveOrZero long throughId){}
 @PutMapping("/read-all") public void all(@Valid @RequestBody ReadAll body,Principal p){service.readAll(body.throughId(),p);}
 @GetMapping("/{id}/target") public Map<String,Object> target(@PathVariable long id,Principal p){return service.target(id,p);}
}
