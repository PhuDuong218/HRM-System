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
    @Test void contractTypeCodeIsUnique(){var repo=mock(ContractTypeRepository.class);when(repo.existsByTypeCode("CT_TEST")).thenReturn(true);status(409,()->new ContractTypeService(repo).create(new ContractTypeRequest("CT_TEST","Name",true)));verify(repo,never()).saveAndFlush(any());}
    @Test void contractTypeDeleteDeactivatesAndPreservesRow(){var repo=mock(ContractTypeRepository.class);var type=contractType();when(repo.findById(2L)).thenReturn(Optional.of(type));new ContractTypeService(repo).deactivate(2L);assertFalse(type.getIsActive());verify(repo,never()).delete(any());}
    @Test void contractTypeCodeCannotChange(){var repo=mock(ContractTypeRepository.class);when(repo.findById(2L)).thenReturn(Optional.of(contractType()));status(400,()->new ContractTypeService(repo).update(2L,new ContractTypeRequest("OTHER","Name",true)));}
    @Test void allowanceTypeCodeIsUnique(){var repo=mock(AllowanceTypeRepository.class);when(repo.existsByTypeCode("PC_TEST")).thenReturn(true);var s=new AllowanceTypeService(repo,mock(BusinessAuditService.class),CLOCK);status(409,()->s.create(new AllowanceTypeRequest("PC_TEST","Name",null,true,"Test")));}
    @Test void allowanceTypeDeleteIsSoftAndAudited(){var repo=mock(AllowanceTypeRepository.class);var audit=mock(BusinessAuditService.class);var type=allowanceType();when(repo.findByIdForUpdate(2L)).thenReturn(Optional.of(type));new AllowanceTypeService(repo,audit,CLOCK).deactivate(2L,"Test");assertFalse(type.getIsActive());verify(repo,never()).delete(any());verify(audit).record(eq("allowance_types"),eq(2L),eq("ALLOWANCE_TYPE_DEACTIVATE"),eq("Test"),any(),any());}
    @Test void deactivationRequiresReason(){var s=new AllowanceTypeService(mock(AllowanceTypeRepository.class),mock(BusinessAuditService.class),CLOCK);status(409,()->s.deactivate(2L," "));}
}
