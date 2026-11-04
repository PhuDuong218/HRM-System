package com.hrm.service;

import com.hrm.dto.ContractCreateRequest;
import com.hrm.enums.*;
import com.hrm.model.*;
import com.hrm.repository.ContractRepository;
import com.hrm.exception.BusinessException;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Called while holding the employee lock, including renew -> create. */
@Service
public class ContractTermPolicy {
    private final ContractRepository contracts;
    private final int gap;
    private final Map<ProbationGroup,Integer> probationDays;
    public ContractTermPolicy(ContractRepository contracts,
            @Value("${hrm.contracts.consecutive-gap-days:30}") int gap,
            @Value("${hrm.contracts.probation-days.QUAN_LY_DN:180}") int manager,
            @Value("${hrm.contracts.probation-days.CAO_DANG_TRO_LEN:60}") int college,
            @Value("${hrm.contracts.probation-days.TRUNG_CAP_NGHIEP_VU:30}") int skilled,
            @Value("${hrm.contracts.probation-days.CONG_VIEC_KHAC:6}") int other) {
        if(gap<0 || manager<1 || college<1 || skilled<1 || other<1) throw new IllegalArgumentException("Invalid contract policy configuration");
        this.contracts=contracts;this.gap=gap;
        probationDays=Map.of(ProbationGroup.QUAN_LY_DN,manager,ProbationGroup.CAO_DANG_TRO_LEN,college,
                ProbationGroup.TRUNG_CAP_NGHIEP_VU,skilled,ProbationGroup.CONG_VIEC_KHAC,other);
    }
    public void validate(Long employeeId, ContractType type, ContractCreateRequest r) {
        var kind=type.getTermKind();
        if(kind==null) throw bad("Loại hợp đồng chưa được phân loại termKind");
        if(kind==ContractTermKind.CTV_CAN_RA_SOAT)
            throw conflict("Cộng tác viên cần rà soát bản chất quan hệ trước khi tạo/gia hạn; không suy ra từ tên hợp đồng");
        if(kind==ContractTermKind.KHONG_XAC_DINH && r.endDate()!=null) throw bad("KHONG_XAC_DINH yêu cầu endDate null");
        if(kind!=ContractTermKind.KHONG_XAC_DINH && r.endDate()==null) throw bad("Hợp đồng có thời hạn/thử việc bắt buộc ngày cuối");
        if(kind!=ContractTermKind.THU_VIEC && r.probationGroup()!=null) throw bad("Chỉ THU_VIEC được chọn probationGroup");
        if(kind==ContractTermKind.XAC_DINH) {
            Integer months=type.getMaxMonths();
            if(months==null || months<1 || months>36) throw bad("maxMonths phải từ 1 đến 36");
            // Inclusive last day: e.g. 01/08/2026 through 31/07/2029 is 36 months.
            if(!r.endDate().isBefore(r.startDate().plusMonths(months))) throw bad("Thời hạn vượt maxMonths (không quá 36 tháng)");
        }
        var history=new ArrayList<>(contracts.findByEmployee_EmployeeIdOrderByStartDateAscContractIdAsc(employeeId));
        history.removeIf(c -> c.getStatus() == ContractStatus.DA_HUY);
        if(kind==ContractTermKind.THU_VIEC) {
            if(r.probationGroup()==null) throw bad("Thử việc bắt buộc probationGroup");
            if(ChronoUnit.DAYS.between(r.startDate(),r.endDate())+1>probationDays.get(r.probationGroup())) throw bad("Thử việc vượt số ngày cấu hình của nhóm");
            if(history.stream().anyMatch(c->c.getContractType().getTermKind()==ContractTermKind.THU_VIEC))
                throw conflict("Mỗi nhân viên chỉ được một hợp đồng thử việc");
        }
        // Include future contracts as well so inserting a backdated contract cannot bypass the limit.
        var candidate=new Contract();candidate.setContractType(type);candidate.setStartDate(r.startDate());candidate.setEndDate(r.endDate());
        history.add(candidate);history.sort(Comparator.comparing(Contract::getStartDate));
        Contract previous=null;int count=0;
        for(var c:history) {
            var k=c.getContractType().getTermKind();
            if(k==null) throw conflict("Lịch sử hợp đồng chưa được phân loại");
            if(k==ContractTermKind.CTV_CAN_RA_SOAT) {
                if(kind==ContractTermKind.XAC_DINH) throw conflict("Lịch sử cộng tác viên chưa được rà soát, chưa thể xác định chuỗi XAC_DINH");
                continue;
            }
            if(k==ContractTermKind.THU_VIEC) continue;
            if(k==ContractTermKind.KHONG_XAC_DINH){previous=null;count=0;continue;}
            if(c.getEndDate()==null) throw conflict("Lịch sử XAC_DINH thiếu ngày cuối");
            long emptyDays=previous==null?Long.MAX_VALUE:ChronoUnit.DAYS.between(previous.getEndDate(),c.getStartDate())-1;
            count=previous!=null && emptyDays<=gap?count+1:1;
            if(count>=3) throw conflict("XAC_DINH thứ 3 trong chuỗi; yêu cầu chuyển sang KHONG_XAC_DINH");
            previous=c;
        }
    }
    private BusinessException bad(String m){return new BusinessException(HttpStatus.BAD_REQUEST,m);}
    private BusinessException conflict(String m){return new BusinessException(HttpStatus.CONFLICT,m);}
}
