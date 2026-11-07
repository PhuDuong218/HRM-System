package com.hrm.duong;
import com.hrm.dto.*;
import com.hrm.enums.*;
import com.hrm.model.*;
import com.hrm.repository.*;
import com.hrm.service.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.hrm.duong.TestData.*;

class ContractTermPolicyTest {
    @Test void legacyLongFixedTermRemainsReadableButNewContractStillHasLimit(){var legacy=old(start.minusYears(6),start.minusDays(1),ContractTermKind.XAC_DINH);rows(legacy);policy.validate(1L,type(ContractTermKind.XAC_DINH),req(start,start.plusMonths(36).minusDays(1),null));status(400,()->policy.validate(1L,type(ContractTermKind.XAC_DINH),req(start,start.plusMonths(36),null)));assertEquals(start.minusYears(6),legacy.getStartDate());}
    @Test void pendingCollaboratorIsNotAssumedToBeExemptLabor(){status(409,()->policy.validate(1L,type(ContractTermKind.CTV_CAN_RA_SOAT),req(start,start.plusDays(5),null)));rows(old(start.minusYears(1),start.minusDays(1),ContractTermKind.CTV_CAN_RA_SOAT));status(409,()->policy.validate(1L,type(ContractTermKind.XAC_DINH),req(start,start.plusDays(5),null)));}
    ContractRepository repo;ContractTermPolicy policy;
    final LocalDate start=LocalDate.of(2026,1,1);
    @BeforeEach void setup(){repo=mock(ContractRepository.class);policy=new ContractTermPolicy(repo,30,180,60,30,6);}
    ContractType type(ContractTermKind k){var t=contractType();t.setTermKind(k);t.setMaxMonths(k==ContractTermKind.XAC_DINH?36:null);return t;}
    ContractCreateRequest req(LocalDate from,LocalDate to,ProbationGroup group){return new ContractCreateRequest(1L,2L,"HD",start,from,to,group);}
    Contract old(LocalDate from,LocalDate to,ContractTermKind k){var c=contract(1,from,to);c.setContractType(type(k));return c;}
    void rows(Contract... c){when(repo.findByEmployee_EmployeeIdOrderByStartDateAscContractIdAsc(1L)).thenReturn(List.of(c));}
    @Test void indefiniteRequiresNullEnd(){var t=type(ContractTermKind.KHONG_XAC_DINH);policy.validate(1L,t,req(start,null,null));status(400,()->policy.validate(1L,t,req(start,start.plusDays(5),null)));}
    @Test void fixedRequiresEndAndAllowsExactly36InclusiveMonths(){var t=type(ContractTermKind.XAC_DINH);policy.validate(1L,t,req(start,start.plusMonths(36).minusDays(1),null));status(400,()->policy.validate(1L,t,req(start,null,null)));status(400,()->policy.validate(1L,t,req(start,start.plusMonths(36),null)));}
    @Test void perTypeMaximumIsApplied(){var t=type(ContractTermKind.XAC_DINH);t.setMaxMonths(12);policy.validate(1L,t,req(start,start.plusMonths(12).minusDays(1),null));status(400,()->policy.validate(1L,t,req(start,start.plusMonths(12),null)));t.setMaxMonths(37);status(400,()->policy.validate(1L,t,req(start,start.plusMonths(1),null)));}
    @Test void secondInChainAllowedThirdRejectedAt30EmptyDays(){rows(old(start,start.plusDays(9),ContractTermKind.XAC_DINH));policy.validate(1L,type(ContractTermKind.XAC_DINH),req(start.plusDays(40),start.plusDays(50),null));rows(old(start,start.plusDays(9),ContractTermKind.XAC_DINH),old(start.plusDays(40),start.plusDays(50),ContractTermKind.XAC_DINH));status(409,()->policy.validate(1L,type(ContractTermKind.XAC_DINH),req(start.plusDays(81),start.plusDays(90),null)));}
    @Test void gap31ResetsAndGapZeroContinues(){rows(old(start,start.plusDays(9),ContractTermKind.XAC_DINH),old(start.plusDays(10),start.plusDays(19),ContractTermKind.XAC_DINH));policy.validate(1L,type(ContractTermKind.XAC_DINH),req(start.plusDays(51),start.plusDays(60),null));status(409,()->policy.validate(1L,type(ContractTermKind.XAC_DINH),req(start.plusDays(20),start.plusDays(30),null)));}
    @Test void probationDoesNotResetFixedChain(){rows(old(start,start.plusDays(9),ContractTermKind.XAC_DINH),old(start.plusDays(10),start.plusDays(12),ContractTermKind.THU_VIEC),old(start.plusDays(13),start.plusDays(19),ContractTermKind.XAC_DINH));status(409,()->policy.validate(1L,type(ContractTermKind.XAC_DINH),req(start.plusDays(20),start.plusDays(30),null)));}
    @Test void indefiniteEndsChain(){rows(old(start,start.plusDays(4),ContractTermKind.XAC_DINH),old(start.plusDays(5),start.plusDays(9),ContractTermKind.XAC_DINH),old(start.plusDays(10),start.plusDays(19),ContractTermKind.KHONG_XAC_DINH));policy.validate(1L,type(ContractTermKind.XAC_DINH),req(start.plusDays(20),start.plusDays(30),null));}
    @Test void insertingPastContractCannotCreateThirdFutureContract(){rows(old(start.plusDays(10),start.plusDays(19),ContractTermKind.XAC_DINH),old(start.plusDays(20),start.plusDays(29),ContractTermKind.XAC_DINH));status(409,()->policy.validate(1L,type(ContractTermKind.XAC_DINH),req(start,start.plusDays(9),null)));}
    @Test void gapIsConfigurable(){rows(old(start,start.plusDays(4),ContractTermKind.XAC_DINH),old(start.plusDays(5),start.plusDays(9),ContractTermKind.XAC_DINH));new ContractTermPolicy(repo,0,180,60,30,6).validate(1L,type(ContractTermKind.XAC_DINH),req(start.plusDays(11),start.plusDays(20),null));}
    @ParameterizedTest @EnumSource(ProbationGroup.class)
    void probationBoundaryInclusive(ProbationGroup g){int days=switch(g){case QUAN_LY_DN->180;case CAO_DANG_TRO_LEN->60;case TRUNG_CAP_NGHIEP_VU->30;case CONG_VIEC_KHAC->6;};policy.validate(1L,type(ContractTermKind.THU_VIEC),req(start,start.plusDays(days-1),g));status(400,()->policy.validate(1L,type(ContractTermKind.THU_VIEC),req(start,start.plusDays(days),g)));}
    @Test void probationRequiresGroupAndEnd(){status(400,()->policy.validate(1L,type(ContractTermKind.THU_VIEC),req(start,start.plusDays(5),null)));status(400,()->policy.validate(1L,type(ContractTermKind.THU_VIEC),req(start,null,ProbationGroup.CONG_VIEC_KHAC)));status(400,()->policy.validate(1L,type(ContractTermKind.XAC_DINH),req(start,start.plusDays(5),ProbationGroup.CONG_VIEC_KHAC)));}
    @Test void probationOnlyOnceIncludingEndedHistory(){rows(old(start.minusYears(1),start.minusYears(1).plusDays(5),ContractTermKind.THU_VIEC));status(409,()->policy.validate(1L,type(ContractTermKind.THU_VIEC),req(start,start.plusDays(5),ProbationGroup.CONG_VIEC_KHAC)));}
    @Test void configurableProbationLimit(){new ContractTermPolicy(repo,30,2,2,2,2).validate(1L,type(ContractTermKind.THU_VIEC),req(start,start.plusDays(1),ProbationGroup.QUAN_LY_DN));status(400,()->new ContractTermPolicy(repo,30,2,2,2,2).validate(1L,type(ContractTermKind.THU_VIEC),req(start,start.plusDays(2),ProbationGroup.QUAN_LY_DN)));}
}
