package com.hrm.service;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
@Component
public class EmployeeCodeActivationJob {
 private final EmployeeCodeService service;
 public EmployeeCodeActivationJob(EmployeeCodeService service){this.service=service;}
 @Scheduled(fixedDelayString="${hrm.employee-code.activation-ms:60000}")
 public void activate(){service.syncDue();}
}
