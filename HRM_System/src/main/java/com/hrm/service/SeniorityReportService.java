package com.hrm.service;

import java.security.Principal;
import java.time.*;
import java.text.Normalizer;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SeniorityReportService {
    private final JdbcTemplate db;
    private final PersonnelAccessService access;
    private final PersonnelScopeService scope;
    private final PersonnelWorkflowSupport workflow;
    public SeniorityReportService(JdbcTemplate db, PersonnelAccessService access, PersonnelScopeService scope, PersonnelWorkflowSupport workflow) {
        this.db=db; this.access=access; this.scope=scope; this.workflow=workflow;
    }
    public record Tenure(int years,int months,int days,int totalMonths) {}
    public record Row(long employeeId,String employeeCode,String fullName,LocalDate hireDate,LocalDate endDate,String status,Tenure tenure) {}
    public record Report(LocalDate asOf,long totalEmployees,long underOneYear,long oneToFiveYears,long fiveYearsOrMore,List<Row> items,int page,int totalPages) {}
    // Calendar anniversaries, not a division by 365. Termination is the final working date.
    static Tenure calculate(LocalDate hire,LocalDate termination,LocalDate asOf) {
        LocalDate end=termination!=null&&termination.isBefore(asOf)?termination:asOf;
        if(hire==null||hire.isAfter(end)) return new Tenure(0,0,0,0);
        Period p=Period.between(hire,end);
        return new Tenure(p.getYears(),p.getMonths(),p.getDays(),p.getYears()*12+p.getMonths());
    }
    static String normalize(String s) {
        return Normalizer.normalize(s==null?"":s,Normalizer.Form.NFD).replaceAll("\\p{M}","").replace('đ','d').replace('Đ','D').toLowerCase(Locale.ROOT).trim();
    }
    @Transactional(readOnly=true)
    public Report report(LocalDate date,String keyword,int page,int size,Principal principal) {
        var actor=access.manager(principal);
        LocalDate asOf=date==null?workflow.today():date;
        if(asOf.isAfter(workflow.today())) throw PersonnelWorkflowSupport.bad("Ngày thống kê không được ở tương lai");
        if(page<0||size<1||size>100) throw PersonnelWorkflowSupport.bad("Phân trang không hợp lệ");
        if(keyword!=null&&keyword.length()>200) throw PersonnelWorkflowSupport.bad("Từ khóa tối đa 200 ký tự");
        String filter="HR".equals(actor.role())?" AND "+scope.sqlEmployees("e.employee_id"):"";
        List<Row> rows=db.query("SELECT e.employee_id,e.employee_code,e.full_name,e.hire_date,e.termination_date,e.employment_status FROM dbo.employees e WHERE e.hire_date<=?"+filter+" ORDER BY e.hire_date,e.employee_id",(r,n)->{
            LocalDate hire=r.getObject(4,LocalDate.class),last=r.getObject(5,LocalDate.class);
            LocalDate end=last!=null&&last.isBefore(asOf)?last:asOf;
            return new Row(r.getLong(1),r.getString(2),r.getString(3),hire,end,r.getString(6),calculate(hire,last,asOf));
        },asOf);
        String q=normalize(keyword);
        rows=rows.stream().filter(r->normalize(r.employeeCode()+" "+r.fullName()).contains(q)).toList();
        long under=rows.stream().filter(r->r.tenure().totalMonths()<12).count();
        long five=rows.stream().filter(r->r.tenure().totalMonths()>=60).count();
        long start=Math.min((long)page*size,rows.size());
        return new Report(asOf,rows.size(),under,rows.size()-under-five,five,rows.subList((int)start,(int)Math.min(start+size,rows.size())),page,(rows.size()+size-1)/size);
    }
}
