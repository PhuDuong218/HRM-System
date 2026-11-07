package com.hrm.config;

import java.io.IOException;
import java.util.UUID;
import com.hrm.util.AuditCorrelation;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component @Order(-200)
public class AuditCorrelationFilter extends OncePerRequestFilter {
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        UUID id;
        try{id=UUID.fromString(request.getHeader("X-Correlation-Id"));}catch(RuntimeException ex){id=UUID.randomUUID();}
        AuditCorrelation.set(id);response.setHeader("X-Correlation-Id",id.toString());
        try{chain.doFilter(request,response);}finally{AuditCorrelation.clear();}
    }
}
