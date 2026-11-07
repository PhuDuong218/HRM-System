package com.hrm.duong;

import java.util.*;
import com.hrm.dto.*;
import com.hrm.repository.*;
import com.hrm.service.*;
import org.junit.jupiter.api.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.hrm.duong.TestData.*;

class CatalogServicesTest {
    @Test void validContractKindsAndMonthsAreStored(){for(var k:List.of(com.hrm.enums.ContractTermKind.XAC_DINH,com.hrm.enums.ContractTermKind.KHONG_XAC_DINH,com.hrm.enums.ContractTermKind.THU_VIEC)){var repo=mock(ContractTypeRepository.class);when(repo.saveAndFlush(any())).thenAnswer(x->x.getArgument(0));var r=new ContractTypeService(repo,mock(BusinessAuditService.class),CLOCK).create(new ContractTypeRequest("T","Type",true,k,k==com.hrm.enums.ContractTermKind.XAC_DINH?36:null,"Test"));assertEquals(k,r.termKind());}}
    @Test void invalidKindOrMonthCombinationsAreRejected(){var s=new ContractTypeService(mock(ContractTypeRepository.class),mock(BusinessAuditService.class),CLOCK);status(400,()->s.create(new ContractTypeRequest("T","Type",true,null,null,"Test")));for(Integer m:Arrays.asList(null,0,37))status(400,()->s.create(new ContractTypeRequest("T","Type",true,com.hrm.enums.ContractTermKind.XAC_DINH,m,"Test")));for(var k:List.of(com.hrm.enums.ContractTermKind.THU_VIEC,com.hrm.enums.ContractTermKind.KHONG_XAC_DINH))status(400,()->s.create(new ContractTypeRequest("T","Type",true,k,1,"Test")));}
    @Test void typeRuleChangeCannotReinterpretHistory(){var repo=mock(ContractTypeRepository.class);when(repo.saveAndFlush(any())).thenAnswer(x->x.getArgument(0));when(repo.findById(2L)).thenReturn(Optional.of(contractType()));var s=new ContractTypeService(repo,mock(BusinessAuditService.class),CLOCK);status(409,()->s.update(2L,new ContractTypeRequest("CT_TEST","Name",true,com.hrm.enums.ContractTermKind.KHONG_XAC_DINH,null,"Test")));assertEquals(com.hrm.enums.ContractTermKind.XAC_DINH,s.update(2L,new ContractTypeRequest("CT_TEST","Name",true,com.hrm.enums.ContractTermKind.XAC_DINH,36,"Test")).termKind());}
    @Test void contractTypeCodeIsUnique(){var repo=mock(ContractTypeRepository.class);when(repo.existsByTypeCode("CT_TEST")).thenReturn(true);status(409,()->new ContractTypeService(repo,mock(BusinessAuditService.class),CLOCK).create(new ContractTypeRequest("CT_TEST","Name",true,com.hrm.enums.ContractTermKind.XAC_DINH,36,"Test")));verify(repo,never()).saveAndFlush(any());}
    @Test void contractTypeDeleteDeactivatesAndPreservesRow(){var repo=mock(ContractTypeRepository.class);var type=contractType();when(repo.findById(2L)).thenReturn(Optional.of(type));new ContractTypeService(repo,mock(BusinessAuditService.class),CLOCK).deactivate(2L);assertFalse(type.getIsActive());verify(repo,never()).delete(any());}
    @Test void contractTypeCodeCannotChange(){var repo=mock(ContractTypeRepository.class);when(repo.findById(2L)).thenReturn(Optional.of(contractType()));status(400,()->new ContractTypeService(repo,mock(BusinessAuditService.class),CLOCK).update(2L,new ContractTypeRequest("OTHER","Name",true,com.hrm.enums.ContractTermKind.XAC_DINH,36,"Test")));}
    @Test void allowanceTypeCodeIsUnique(){var repo=mock(AllowanceTypeRepository.class);when(repo.existsByTypeCode("PC_TEST")).thenReturn(true);var s=new AllowanceTypeService(repo,mock(BusinessAuditService.class),CLOCK);status(409,()->s.create(new AllowanceTypeRequest("PC_TEST","Name",null,true,"Test",null,null)));}

    @Test void allowanceTypeFlagsDefaultAndCanBeChangedAndAudited() {
        var repo = mock(AllowanceTypeRepository.class);
        var audit = mock(BusinessAuditService.class);
        var service = new AllowanceTypeService(repo,audit,CLOCK);
        var before = service.create(new AllowanceTypeRequest("PC_TEST","Type",null,true,"Create",null,null));
        assertTrue(before.isTaxable());
        assertFalse(before.countsForSocialInsurance());

        var entity = allowanceType();
        when(repo.findByIdForUpdate(2L)).thenReturn(Optional.of(entity));
        var updated = service.update(2L,new AllowanceTypeRequest("PC_TEST",entity.getTypeName(),null,true,
                "Update flags",false,true));
        assertFalse(updated.isTaxable());
        assertTrue(updated.countsForSocialInsurance());
        verify(audit).record(eq("allowance_types"),eq(2L),eq("ALLOWANCE_TYPE_UPDATE"),eq("Update flags"),
                argThat(value -> value instanceof AllowanceTypeResponse type && type.isTaxable()
                        && !type.countsForSocialInsurance()),eq(updated));

        var legacyUpdate = service.update(2L,new AllowanceTypeRequest("PC_TEST",entity.getTypeName(),null,true,
                "Update name",null,null));
        assertFalse(legacyUpdate.isTaxable());
        assertTrue(legacyUpdate.countsForSocialInsurance());
    }
    @Test void allowanceTypeDeleteIsSoftAndAudited(){var repo=mock(AllowanceTypeRepository.class);var audit=mock(BusinessAuditService.class);var type=allowanceType();when(repo.findByIdForUpdate(2L)).thenReturn(Optional.of(type));new AllowanceTypeService(repo,audit,CLOCK).deactivate(2L,"Test");assertFalse(type.getIsActive());verify(repo,never()).delete(any());verify(audit).record(eq("allowance_types"),eq(2L),eq("ALLOWANCE_TYPE_DEACTIVATE"),eq("Test"),any(),any());}
    @Test void deactivationRequiresReason(){var s=new AllowanceTypeService(mock(AllowanceTypeRepository.class),mock(BusinessAuditService.class),CLOCK);status(409,()->s.deactivate(2L," "));}
}
