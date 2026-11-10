package com.hrm.controller;
import java.security.Principal;import com.hrm.service.*;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/employee-requests/evidence") public class LeaveEvidenceController {
 private final PersonnelAccessService access;private final EmployeeRequestService requests;private final LeaveEvidenceStore files;
 public LeaveEvidenceController(PersonnelAccessService access,EmployeeRequestService requests,LeaveEvidenceStore files){this.access=access;this.requests=requests;this.files=files;}
 @PostMapping public LeaveEvidenceStore.FileData upload(@RequestBody LeaveEvidenceStore.Upload body,Principal p){return files.upload(access.actor(p).accountId(),body.content());}
 @GetMapping("/{requestId}") public LeaveEvidenceStore.FileData read(@PathVariable long requestId,Principal p){var detail=requests.detail(requestId,p);return files.read((String)detail.get("evidenceReference"));}
}
