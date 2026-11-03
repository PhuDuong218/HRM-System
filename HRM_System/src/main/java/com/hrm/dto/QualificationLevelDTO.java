package com.hrm.dto;

public record QualificationLevelDTO(
        Long qualificationLevelId,
        String levelCode,
        String levelName,
        String description,
        Boolean isActive,Integer rankOrder) {
    public QualificationLevelDTO(Long id,String code,String name,String description,Boolean active){this(id,code,name,description,active,null);}
}
