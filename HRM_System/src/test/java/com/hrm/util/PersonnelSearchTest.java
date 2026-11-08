package com.hrm.util;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PersonnelSearchTest {
 @Test void selectedEmployeeLabelMatchesWithoutRequiringSeparator(){for(String separator:new String[]{" — "," – "," - "})assertTrue(PersonnelSearch.matches("TV00001"+separator+"Phan Tuyết Nhi","TV00001","Phan Tuyết Nhi","Đào tạo"));}
 @Test void partialNamesIgnoreVietnameseAccentsAndCase(){for(String q:new String[]{"t","tuyet","PHAN TUYET","nhi","tv000"})assertTrue(PersonnelSearch.matches(q,"TV00001","Phan Tuyết Nhi"));}
 @Test void employeeCodeHyphensRemainSignificant(){assertTrue(PersonnelSearch.matches("EMP-001","EMP-001","Trần Thị A"));assertFalse(PersonnelSearch.matches("EMP-999","EMP-001","Trần Thị A"));}
 @Test void unrelatedEmployeeDoesNotMatchAndBlankShowsAll(){assertFalse(PersonnelSearch.matches("TV00002 — Nguyễn Văn B","TV00001","Phan Tuyết Nhi"));assertTrue(PersonnelSearch.matches("  ","TV00001","Phan Tuyết Nhi"));}
}
