package com.hrm.service;

import com.hrm.dto.*;
import com.hrm.model.*;
import com.hrm.repository.*;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EmployeeWriteFixRegressionTest {
    private Set<String> invalid(Object dto) {
        try(var factory=Validation.buildDefaultValidatorFactory()) {
            var fields=new HashSet<String>();
            factory.getValidator().validate(dto).forEach(v->fields.add(v.getPropertyPath().toString()));
            return fields;
        }
    }
    @Test void creatingRequiresAllThreeContactFields() {
        for(String value:new String[]{null,"","   "}) {
            var dto=new EmployeeRequestDTO();dto.setEmail(value);dto.setIdentityNumber(value);dto.setAddress(value);
            assertTrue(invalid(dto).containsAll(Set.of("email","identityNumber","address")));
        }
    }
    @Test void malformedIdentityIsRejectedOnBothWriteDtos() {
        for(String value:List.of("CCCDabc","12345678","1234567890","12345678901","1234567890123","12345678901a","123 456789012")) {
            var create=new EmployeeRequestDTO();create.setIdentityNumber(value);
            var edit=new EmployeeUpdateRequestDTO();edit.setIdentityNumber(value);
            assertTrue(invalid(create).contains("identityNumber"),value);
            assertTrue(invalid(edit).contains("identityNumber"),value);
        }
    }
    @Test void profileAndContactRequireReasonAndReadVersion() {
        assertTrue(invalid(new EmployeeUpdateRequestDTO()).containsAll(Set.of("reason","version")));
        assertTrue(invalid(new EmployeeContactPatchDTO()).containsAll(Set.of("reason","version")));
    }
    @Test void duplicateQualificationCannotBeCreated() {
        var repo=mock(EmployeeQualificationRepository.class);var employees=mock(EmployeeRepository.class);var levels=mock(QualificationLevelRepository.class);
        var employee=new Employee();employee.setEmployeeId(700L);
        var level=new QualificationLevel();level.setQualificationLevelId(2L);level.setIsActive(true);
        var existing=new EmployeeQualification();existing.setEmployee(employee);existing.setQualificationLevel(level);existing.setMajor("Quản trị");existing.setInstitution("Trường QA");existing.setGraduationYear(2020);existing.setCertificateNumber("QAFIX-A");
        when(employees.findByIdForUpdate(700L)).thenReturn(Optional.of(employee));when(levels.findById(2L)).thenReturn(Optional.of(level));
        when(repo.findAllByEmployeeWithLevel(700L)).thenReturn(List.of(existing));when(repo.saveAndFlush(any())).thenAnswer(i->i.getArgument(0));
        var dto=new EmployeeQualificationRequestDTO();dto.setQualificationLevelId(2L);dto.setMajor(" quản trị ");dto.setInstitution("trường qa");dto.setGraduationYear(2020);dto.setCertificateNumber("qafix-a");
        var service=new EmployeeQualificationService(repo,employees,levels);
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.addEmployeeQualification(700L,dto)).getStatusCode().value());
        verify(repo,never()).saveAndFlush(any());
    }
}
