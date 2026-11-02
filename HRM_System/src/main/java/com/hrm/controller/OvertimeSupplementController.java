package com.hrm.controller;
import com.hrm.security.AuthenticatedAccount;
import com.hrm.service.OvertimeSupplementService;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/overtime/workspace/supplements")
public class OvertimeSupplementController {
 private final OvertimeSupplementService service;
 public OvertimeSupplementController(OvertimeSupplementService service){this.service=service;}
 @GetMapping("/candidates") public Object candidates(@AuthenticationPrincipal AuthenticatedAccount p,@RequestParam LocalDate from,@RequestParam LocalDate to,@RequestParam(defaultValue="0") int page){return service.candidates(p,from,to,page);}
 @GetMapping public Object list(@AuthenticationPrincipal AuthenticatedAccount p,@RequestParam(defaultValue="true") boolean mine,@RequestParam LocalDate from,@RequestParam LocalDate to,@RequestParam(defaultValue="0") int page){return service.list(p,mine,from,to,page);}
 @PostMapping("/overtime/{id}") public Object submit(@AuthenticationPrincipal AuthenticatedAccount p,@PathVariable long id,@RequestBody OvertimeSupplementService.Proposal body){return Map.of("requestId",service.submit(p,id,body));}
 public record Decision(boolean approve,String reason){}
 @PostMapping("/{id}/decision") public void decide(@AuthenticationPrincipal AuthenticatedAccount p,@PathVariable long id,@RequestBody Decision body){service.decide(p,id,body.approve(),body.reason());}
}
