package com.hrm.controller;
import java.time.*;import com.hrm.service.*;import com.hrm.security.AuthenticatedAccount;import org.springframework.web.bind.annotation.*;import org.springframework.security.core.annotation.AuthenticationPrincipal;
@RestController @RequestMapping("/api/department-workspace") public class DepartmentWorkspaceController {
 private final DepartmentWorkspaceService service;private final DepartmentReviewService reviews;
 public DepartmentWorkspaceController(DepartmentWorkspaceService service,DepartmentReviewService reviews){this.service=service;this.reviews=reviews;}
 @GetMapping("/options") public Object options(@AuthenticationPrincipal AuthenticatedAccount p){return service.options(p);}
 @GetMapping("/list/{kind}") public Object list(@AuthenticationPrincipal AuthenticatedAccount p,@PathVariable String kind,@RequestParam LocalDate from,@RequestParam LocalDate to,@RequestParam(required=false) String q,@RequestParam(required=false) String status,@RequestParam(required=false) Long employeeId,@RequestParam(defaultValue="0") int page){return service.list(p,kind,from,to,q,status,employeeId,page);}
 @GetMapping("/summary") public Object summary(@AuthenticationPrincipal AuthenticatedAccount p,@RequestParam LocalDate from,@RequestParam LocalDate to,@RequestParam(required=false) Long employeeId){return service.summary(p,from,to,employeeId);}
 @GetMapping("/personnel/{id}") public Object personnel(@AuthenticationPrincipal AuthenticatedAccount p,@PathVariable long id){return service.personnel(p,id);}
 @GetMapping("/detail/{kind}/{id}") public Object detail(@AuthenticationPrincipal AuthenticatedAccount p,@PathVariable String kind,@PathVariable long id){return service.detail(p,kind,id);}
 @GetMapping("/reviews/{kind}/{id}") public Object review(@AuthenticationPrincipal AuthenticatedAccount p,@PathVariable String kind,@PathVariable long id){return reviews.detail(p,kind,id);}
 public record Decision(boolean approve,String reason,String expectedVersion){}
 @PostMapping("/reviews/{kind}/{id}") public void decide(@AuthenticationPrincipal AuthenticatedAccount p,@PathVariable String kind,@PathVariable long id,@RequestBody Decision body){reviews.decide(p,kind,id,body.approve(),body.reason(),body.expectedVersion());}
 @PostMapping("/schedules/{id}/cancel") public Object cancel(@AuthenticationPrincipal AuthenticatedAccount p,@PathVariable long id,@RequestBody Decision body){return service.cancel(p,id,body.reason());}
 @PostMapping("/schedules") public Object create(@AuthenticationPrincipal AuthenticatedAccount p,@RequestBody ScheduleWorkspaceService.SaveInput body){return service.save(p,null,body);}
 @PutMapping("/schedules/{id}") public Object update(@AuthenticationPrincipal AuthenticatedAccount p,@PathVariable long id,@RequestBody ScheduleWorkspaceService.SaveInput body){return service.save(p,id,body);}
}
