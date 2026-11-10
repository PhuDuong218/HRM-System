package com.hrm.service;
import com.hrm.dto.*;import jakarta.validation.Validation;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class EmployeeGenderRequiredTest {
 @Test void creationAndFullProfileUpdateRequireGender(){try(var factory=Validation.buildDefaultValidatorFactory()){var v=factory.getValidator();assertFalse(v.validateProperty(new EmployeeRequestDTO(),"gender").isEmpty());assertFalse(v.validateProperty(new EmployeeUpdateRequestDTO(),"gender").isEmpty());var request=new EmployeeRequestDTO();request.setGender("MALE");assertTrue(v.validateProperty(request,"gender").isEmpty());request.setGender("INVALID");assertFalse(v.validateProperty(request,"gender").isEmpty());}}
}
