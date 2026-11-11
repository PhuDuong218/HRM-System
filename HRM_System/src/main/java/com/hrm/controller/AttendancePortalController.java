package com.hrm.controller;

import com.hrm.dto.AttendanceDTOs.*;
import com.hrm.security.AuthenticatedAccount;
import com.hrm.service.AttendancePortalService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Scoped page API, independent of the legacy task API deployment switch. */
@RestController
@RequestMapping("/api/attendance-workspace")
public class AttendancePortalController {
    private final AttendancePortalService service;
    public AttendancePortalController(AttendancePortalService service){this.service=service;}
    @org.springframework.beans.factory.annotation.Autowired private com.hrm.service.AttendanceClockReviewService clockReview;
    @PostMapping("/records/{id}/review-check-in") public Object reviewIn(@AuthenticationPrincipal AuthenticatedAccount a,@PathVariable long id,@Valid @RequestBody VersionCommand body){return clockReview.review(a,id,true,body.expectedVersion(),body.reason());}
    @PostMapping("/records/{id}/review-check-out") public Object reviewOut(@AuthenticationPrincipal AuthenticatedAccount a,@PathVariable long id,@Valid @RequestBody VersionCommand body){return clockReview.review(a,id,false,body.expectedVersion(),body.reason());}
    @GetMapping("/context") public Object context(@AuthenticationPrincipal AuthenticatedAccount a){return service.context(a);}
    @GetMapping("/calendar-policy") public Object calendarPolicy(@AuthenticationPrincipal AuthenticatedAccount a){return service.calendarInfo(a);}
    @GetMapping("/departments") public Object departments(@AuthenticationPrincipal AuthenticatedAccount a){return service.departments(a);}
    @GetMapping("/pending") public Object pending(@AuthenticationPrincipal AuthenticatedAccount a,@RequestParam(defaultValue="0") int page){return service.pending(a,page);}
    @GetMapping("/records") public Object records(@AuthenticationPrincipal AuthenticatedAccount a,@RequestParam(defaultValue="false") boolean mine,
      @RequestParam LocalDate from,@RequestParam LocalDate to,@RequestParam(required=false) String q,@RequestParam(required=false) Long departmentId,
      @RequestParam(required=false) String verification,@RequestParam(required=false) String anomaly,@RequestParam(defaultValue="0") int page){return service.search(a,mine,from,to,q,departmentId,verification,anomaly,page);}
    @GetMapping("/schedules/{id}") public Object detail(@AuthenticationPrincipal AuthenticatedAccount a,@PathVariable long id,@RequestParam(defaultValue="false") boolean mine){return service.detail(a,mine,id);}
    @PostMapping("/schedules/{id}/remind-missing-clock")
    public void remindMissingClock(@AuthenticationPrincipal AuthenticatedAccount actor, @PathVariable long id) {
        service.remindMissingClock(actor, id);
    }
    @PostMapping("/schedules/{id}/absence") public Object absence(@AuthenticationPrincipal AuthenticatedAccount a,@PathVariable long id,@RequestBody VersionCommand body){return service.markAbsent(a,id,body.reason());}
    @PostMapping("/manual") public Object manual(@AuthenticationPrincipal AuthenticatedAccount a,@Valid @RequestBody AttendanceInput body){return service.manual(a,body);}
    @PutMapping("/records/{id}") public Object correct(@AuthenticationPrincipal AuthenticatedAccount a,@PathVariable long id,@Valid @RequestBody CorrectionCommand body){return service.correctVersioned(a,id,body.correction(),body.expectedVersion());}
    @PostMapping("/records/{id}/confirm") public Object confirm(@AuthenticationPrincipal AuthenticatedAccount a,@PathVariable long id,@Valid @RequestBody VersionCommand body){return service.verifyVersioned(a,id,true,null,body.expectedVersion());}
    public record VersionCommand(@jakarta.validation.constraints.NotBlank String expectedVersion,String reason){}
    public record CorrectionCommand(@jakarta.validation.constraints.NotBlank String expectedVersion,@jakarta.validation.constraints.NotNull @Valid AttendanceCorrection correction){}
    @PostMapping("/records/{id}/unconfirm") public Object unconfirm(@AuthenticationPrincipal AuthenticatedAccount a,@PathVariable long id,@Valid @RequestBody VersionCommand body){return service.verifyVersioned(a,id,false,body.reason(),body.expectedVersion());}
    @PostMapping("/schedules/{id}/check-in") public Object checkIn(@AuthenticationPrincipal AuthenticatedAccount a,@PathVariable long id){return service.clock(a,id,true);}
    @PostMapping("/schedules/{id}/check-out") public Object checkOut(@AuthenticationPrincipal AuthenticatedAccount a,@PathVariable long id){return service.clock(a,id,false);}
    @GetMapping("/requests") public Object requests(@AuthenticationPrincipal AuthenticatedAccount a,@RequestParam(defaultValue="false") boolean mine,
      @RequestParam LocalDate from,@RequestParam LocalDate to,@RequestParam(required=false) String type,@RequestParam(required=false) String status,@RequestParam(defaultValue="0") int page){return service.requests(a,mine,from,to,type,status,page);}
    @GetMapping("/requests/{id}") public Object request(@AuthenticationPrincipal AuthenticatedAccount a,@PathVariable long id,@RequestParam(defaultValue="false") boolean mine){return service.request(a,mine,id);}
    @PostMapping("/requests") public Object submit(@AuthenticationPrincipal AuthenticatedAccount a,@RequestBody AttendancePortalService.Proposal body){return java.util.Map.of("requestId",service.submit(a,body));}
    @PostMapping("/requests/{id}/receive") public void receive(@AuthenticationPrincipal AuthenticatedAccount a,@PathVariable long id){service.receive(a,id);}
    @PostMapping("/requests/{id}/decision") public void decide(@AuthenticationPrincipal AuthenticatedAccount a,@PathVariable long id,@RequestBody AttendancePortalService.Decision body){service.decide(a,id,body);}
}
