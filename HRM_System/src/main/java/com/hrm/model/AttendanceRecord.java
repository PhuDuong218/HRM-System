package com.hrm.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "attendance_records")
public class AttendanceRecord {

    @Id
    private Long attendanceId;
}
