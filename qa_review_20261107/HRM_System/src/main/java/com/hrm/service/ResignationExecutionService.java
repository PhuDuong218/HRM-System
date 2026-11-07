package com.hrm.service;

import java.util.*;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ResignationExecutionService {
    private static final Logger LOG=LoggerFactory.getLogger(ResignationExecutionService.class);
    private final EmployeeRequestService service;
    private final boolean enabled;
    public ResignationExecutionService(EmployeeRequestService service,@Value("${personnel.resignations.scheduler-enabled:${g2.resignations.scheduler-enabled:false}}") boolean enabled){this.service=service;this.enabled=enabled;}
    /** Each request commits separately. A blocked request cannot roll back other employees. */
    public List<Map<String,Object>> run(Long actor) {
        var result=new ArrayList<Map<String,Object>>();
        long cursor=0;
        while(true) {
            var page=service.due(cursor);
            if(page.isEmpty()) break;
            for(Long id:page) {
                try { service.execute(id,actor);result.add(Map.of("requestId",id,"result","APPLIED")); }
                catch(ResponseStatusException ex){result.add(Map.of("requestId",id,"result","BLOCKED","reason",Objects.toString(ex.getReason(),"Conflict")));}
                cursor=id;
            }
            if(page.size()<100) break;
        }
        return result;
    }
    @Scheduled(fixedDelayString="${personnel.resignations.interval-ms:${g2.resignations.interval-ms:60000}}",initialDelayString="${personnel.resignations.initial-delay-ms:${g2.resignations.initial-delay-ms:60000}}")
    public void scheduled() {
        if(!enabled) return;
        try {for(var row:run(null)) if("BLOCKED".equals(row.get("result"))) LOG.warn("Resignation {} blocked: {}",row.get("requestId"),row.get("reason"));}
        catch(RuntimeException ex){LOG.error("Resignation runner failed; no successful execution is assumed",ex);}
    }
}
