package com.hrm.service;

import com.hrm.dto.EmployeeQualificationRequestDTO;
import com.hrm.model.*;
import com.hrm.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.text.Normalizer;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QualificationDuplicateFixTest {
    final EmployeeQualificationRepository repo=mock(EmployeeQualificationRepository.class);
    final EmployeeRepository employees=mock(EmployeeRepository.class);
    final QualificationLevelRepository levels=mock(QualificationLevelRepository.class);
    final EmployeeQualificationService service=new EmployeeQualificationService(repo,employees,levels);
    final Employee employee=new Employee();final QualificationLevel level=new QualificationLevel();
    QualificationDuplicateFixTest(){employee.setEmployeeId(701L);level.setQualificationLevelId(2L);level.setIsActive(true);when(employees.findByIdForUpdate(701L)).thenReturn(Optional.of(employee));when(levels.findById(anyLong())).thenReturn(Optional.of(level));when(repo.saveAndFlush(any())).thenAnswer(i->i.getArgument(0));}
    EmployeeQualification existing(long id){var q=new EmployeeQualification();q.setEmployeeQualificationId(id);q.setEmployee(employee);q.setQualificationLevel(level);q.setMajor("Quản trị");q.setInstitution("Trường QA");q.setGraduationYear(2020);q.setCertificateNumber("QAFIX-A");return q;}
    EmployeeQualificationRequestDTO request(){var r=new EmployeeQualificationRequestDTO();r.setQualificationLevelId(2L);r.setMajor("Quản trị");r.setInstitution("Trường QA");r.setGraduationYear(2020);r.setCertificateNumber("QAFIX-A");return r;}
    @Test void unicodeCaseAndWhitespaceDuplicateIsRejected(){
        when(repo.findAllByEmployeeWithLevel(701L)).thenReturn(List.of(existing(1L)));var r=request();r.setInstitution(Normalizer.normalize(" TRƯỜNG QA ",Normalizer.Form.NFD));
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.addEmployeeQualification(701L,r)).getStatusCode().value());verify(repo,never()).saveAndFlush(any());
    }
    @Test void changingAnyOneKeyAllowsDifferentQualification(){
        when(repo.findAllByEmployeeWithLevel(701L)).thenReturn(List.of(existing(1L)));
        for(int field=0;field<5;field++){var r=request();switch(field){case 0->r.setQualificationLevelId(3L);case 1->r.setMajor("Kế toán");case 2->r.setInstitution("Trường khác");case 3->r.setGraduationYear(2021);case 4->r.setCertificateNumber("QAFIX-B");}assertDoesNotThrow(()->service.addEmployeeQualification(701L,r));}
    }
    @Test void updateCannotBecomeAnotherExistingQualification(){
        var target=existing(2L);target.setMajor("Khác");when(repo.findByIdWithLevel(2L)).thenReturn(Optional.of(target));when(repo.findAllByEmployeeWithLevel(701L)).thenReturn(List.of(existing(1L),target));
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.updateEmployeeQualification(2L,request())).getStatusCode().value());assertEquals("Khác",target.getMajor());verify(repo,never()).saveAndFlush(any());
    }
    @Test void editingItselfIsNotDuplicateAndRemovedRowsAreNotReturnedByRepository(){
        var target=existing(2L);when(repo.findByIdWithLevel(2L)).thenReturn(Optional.of(target));when(repo.findAllByEmployeeWithLevel(701L)).thenReturn(List.of(target));
        assertDoesNotThrow(()->service.updateEmployeeQualification(2L,request()));
        when(repo.findAllByEmployeeWithLevel(701L)).thenReturn(List.of());assertDoesNotThrow(()->service.addEmployeeQualification(701L,request()));
    }
}
