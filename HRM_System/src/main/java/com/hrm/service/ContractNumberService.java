package com.hrm.service;
import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.stereotype.Service;
@Service public class ContractNumberService {
 private final JdbcTemplate db;
 public ContractNumberService(JdbcTemplate db){this.db=db;}
 public String next(String typeCode){Long number=db.queryForObject("SELECT NEXT VALUE FOR dbo.contract_number_sequence",Long.class);if(number==null)throw new IllegalStateException("No contract number allocated");return ContractGeneratedFields.number(typeCode,number);}
}
