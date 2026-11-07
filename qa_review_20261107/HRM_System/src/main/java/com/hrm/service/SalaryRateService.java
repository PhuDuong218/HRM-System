package com.hrm.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import com.hrm.dto.SalaryRateSegment;
import com.hrm.exception.BusinessException;
import com.hrm.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SalaryRateService {
    private final EmployeeRepository employees;
    private final ContractRepository contracts;
    private final ContractSalaryTermRepository terms;

    public SalaryRateService(EmployeeRepository employees,ContractRepository contracts,
                             ContractSalaryTermRepository terms) {
        this.employees=employees; this.contracts=contracts; this.terms=terms;
    }

    @Transactional
    public SalaryRateSegment getRateAt(Long employeeId,LocalDate date) {
        return getRatesForPeriod(employeeId,date,date).getFirst();
    }

    @Transactional
    public List<SalaryRateSegment> getRatesForPeriod(Long employeeId,LocalDate from,LocalDate to) {
        if (from==null || to==null || to.isBefore(from) || ChronoUnit.DAYS.between(from,to)>365) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,"Khoảng đọc phải từ 1 đến 366 ngày");
        }
        // Khóa cả luồng đọc để bộ hợp đồng+mức lương không đổi giữa hai query.
        employees.findByIdForUpdate(employeeId).orElseThrow(() ->
                new BusinessException(HttpStatus.NOT_FOUND,"Không có nhân viên"));
        var contractRows=contracts.findIntersecting(employeeId,from,to);
        var termRows=terms.findIntersecting(employeeId,from,to);
        List<SalaryRateSegment> result=new ArrayList<>();
        int dayCount=Math.toIntExact(ChronoUnit.DAYS.between(from,to))+1;
        for (int offset=0;offset<dayCount;offset++) {
            LocalDate day=from.plusDays(offset);
            var activeContracts=contractRows.stream().filter(c ->
                    !day.isBefore(c.getStartDate()) && (c.getEndDate()==null || !day.isAfter(c.getEndDate()))).toList();
            if (activeContracts.size()!=1) {
                throw new BusinessException(HttpStatus.CONFLICT,
                        "Ngày "+day+": cần đúng một hợp đồng, có "+activeContracts.size());
            }
            Long contractId=activeContracts.getFirst().getContractId();
            var activeTerms=termRows.stream().filter(t ->
                    t.getContract().getContractId().equals(contractId) &&
                    !day.isBefore(t.getEffectiveFrom()) &&
                    (t.getEffectiveTo()==null || !day.isAfter(t.getEffectiveTo()))).toList();
            if (activeTerms.size()!=1) {
                throw new BusinessException(HttpStatus.CONFLICT,
                        "Ngày "+day+": cần đúng một mức lương, có "+activeTerms.size());
            }
            var term=activeTerms.getFirst();
            if (!result.isEmpty() && result.getLast().salaryTermId().equals(term.getSalaryTermId())) {
                var last=result.removeLast();
                result.add(new SalaryRateSegment(employeeId,contractId,term.getSalaryTermId(),
                        last.from(),day,term.getBaseSalary(),term.getPayBasis()));
            } else {
                result.add(new SalaryRateSegment(employeeId,contractId,term.getSalaryTermId(),
                        day,day,term.getBaseSalary(),term.getPayBasis()));
            }
        }
        return List.copyOf(result);
    }
}