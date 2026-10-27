package com.hrm.hrm;

import java.time.LocalDate;

import com.hrm.enums.EmploymentStatus;
import com.hrm.repository.ContractRepository;
import com.hrm.repository.EmployeeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class HrmApplicationTests {

	@Autowired
	private EmployeeRepository employees;

	@Autowired
	private ContractRepository contracts;

	@Test
	void contextLoads() {
	}

	@Test
	void mergedEmployeeQueriesAndContractMappingUseTheTestSchema() {
		assertEquals(0L, contracts.count());
		assertTrue(employees.findAllWithAssignments().isEmpty());
		assertTrue(employees.findByIdWithAssignments(1L).isEmpty());
		assertFalse(employees.existsByEmployeeIdAndEmploymentStatus(1L, EmploymentStatus.DANG_LAM));
		assertTrue(employees.searchEmployees("%", null, null, null,
				LocalDate.of(2026, 10, 27), PageRequest.of(0, 10)).isEmpty());
	}

}
