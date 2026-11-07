package com.hrm.dto;

public record EmployeeQualificationDTO(
        Long employeeQualificationId,
        Long employeeId,
        Long qualificationLevelId,
        String levelCode,
        String levelName,
        String major,
        String institution,
        Integer graduationYear,
        String certificateNumber,
        Boolean isPrimary, java.time.LocalDate issuedDate, java.time.LocalDate expiryDate,
        String validityStatus, Integer rankOrder, Boolean highest) {
    public EmployeeQualificationDTO(Long id,Long employee,Long level,String code,String name,String major,String institution,Integer year,String number,Boolean primary){
        this(id,employee,level,code,name,major,institution,year,number,primary,null,null,"NO_EXPIRY",null,false);
    }
}
