package com.hrm.controller;
import com.hrm.service.SeniorityReportService;
import java.security.Principal;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reports/seniority")
public class SeniorityReportController {
    private final SeniorityReportService service;
    public SeniorityReportController(SeniorityReportService service){this.service=service;}
    @GetMapping
    public SeniorityReportService.Report report(@RequestParam(required=false) LocalDate asOf,@RequestParam(defaultValue="") String keyword,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int size,Principal p){
        return service.report(asOf,keyword,page,size,p);
    }
}
