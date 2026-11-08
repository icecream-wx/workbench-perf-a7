package com.example.perfa7.controller;

import com.example.perfa7.domain.Employee;
import com.example.perfa7.domain.EmployeeStatus;
import com.example.perfa7.domain.Gender;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record EmployeeResponse(
        Long id,
        String employeeNo,
        String name,
        String gender,
        String phone,
        String email,
        String department,
        String position,
        @JsonFormat(pattern = "yyyy-MM-dd") LocalDate hireDate,
        String status,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime createdAt,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime updatedAt) {

    public static EmployeeResponse from(Employee employee) {
        return new EmployeeResponse(
                employee.getId(),
                employee.getEmployeeNo(),
                employee.getName(),
                (employee.getGender() == null ? Gender.UNKNOWN : employee.getGender()).name(),
                employee.getPhone(),
                employee.getEmail(),
                employee.getDepartment(),
                employee.getPosition(),
                employee.getHireDate(),
                (employee.getStatus() == null ? EmployeeStatus.ACTIVE : employee.getStatus()).name(),
                employee.getCreatedAt(),
                employee.getUpdatedAt());
    }
}
