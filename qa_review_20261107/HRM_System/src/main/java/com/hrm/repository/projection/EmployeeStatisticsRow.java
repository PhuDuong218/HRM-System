package com.hrm.repository.projection;

public interface EmployeeStatisticsRow {

    Long getGroupId();

    String getGroupCode();

    String getGroupName();

    Long getEmployeeCount();
}
