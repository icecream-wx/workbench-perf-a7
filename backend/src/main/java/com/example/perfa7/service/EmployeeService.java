package com.example.perfa7.service;

import com.example.perfa7.controller.EmployeeRequest;
import com.example.perfa7.domain.Employee;
import com.example.perfa7.domain.EmployeeStatus;
import com.example.perfa7.domain.Gender;
import com.example.perfa7.exception.BizException;
import com.example.perfa7.exception.FieldErrorItem;
import com.example.perfa7.repository.EmployeeRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class EmployeeService {

    public static final int MAX_PAGE_SIZE = 100;

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    private final EmployeeRepository employeeRepository;

    public EmployeeService(EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
    }

    @Transactional(readOnly = true)
    public Page<Employee> list(String keyword, String department, EmployeeStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.ASC, "id"));
        return employeeRepository.search(clean(keyword), clean(department), status, pageable);
    }

    @Transactional(readOnly = true)
    public Employee get(long id) {
        return employeeRepository.findById(id)
                .orElseThrow(() -> new BizException(org.springframework.http.HttpStatus.NOT_FOUND,
                        "EMPLOYEE_NOT_FOUND", "员工不存在"));
    }

    @Transactional
    public Employee create(EmployeeRequest request) {
        String employeeNo = request.getEmployeeNo();
        if (employeeRepository.existsByEmployeeNoIgnoreCase(employeeNo)) {
            throw duplicateEmployeeNo();
        }
        Employee employee = new Employee();
        applyEditableFields(employee, request, false);
        LocalDateTime now = now();
        employee.setCreatedAt(now);
        employee.setUpdatedAt(now);
        return employeeRepository.save(employee);
    }

    @Transactional
    public Employee update(long id, EmployeeRequest request) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new BizException(org.springframework.http.HttpStatus.NOT_FOUND,
                        "EMPLOYEE_NOT_FOUND", "员工不存在"));
        String employeeNo = request.getEmployeeNo();
        if (employeeRepository.existsByEmployeeNoIgnoreCaseAndIdNot(employeeNo, id)) {
            throw duplicateEmployeeNo();
        }
        applyEditableFields(employee, request, true);
        LocalDateTime now = now();
        if (!now.isAfter(employee.getUpdatedAt())) {
            now = employee.getUpdatedAt().plusSeconds(1);
        }
        employee.setUpdatedAt(now);
        return employeeRepository.save(employee);
    }

    private void applyEditableFields(Employee employee, EmployeeRequest request, boolean statusRequired) {
        employee.setEmployeeNo(request.getEmployeeNo());
        employee.setName(request.getName());
        Gender gender = Gender.fromCode(request.getGender());
        if (gender == null) {
            throw BizException.badRequest("VALIDATION_ERROR", "性别取值不合法", "gender");
        }
        employee.setGender(gender);
        employee.setPhone(request.getPhone());
        employee.setEmail(request.getEmail());
        employee.setDepartment(request.getDepartment());
        employee.setPosition(request.getPosition());
        employee.setHireDate(parseHireDate(request.getHireDate()));
        employee.setStatus(resolveStatus(request.getStatus(), statusRequired));
    }

    private EmployeeStatus resolveStatus(String value, boolean required) {
        if (value == null) {
            if (required) {
                throw BizException.badRequest("VALIDATION_ERROR", "状态不能为空", "status");
            }
            return EmployeeStatus.ACTIVE;
        }
        EmployeeStatus status = EmployeeStatus.fromCode(value);
        if (status == null) {
            throw BizException.badRequest("VALIDATION_ERROR", "状态取值不合法", "status");
        }
        return status;
    }

    private LocalDate parseHireDate(String value) {
        if (value == null) {
            return null;
        }
        LocalDate hireDate;
        try {
            hireDate = LocalDate.parse(value, DATE_FORMATTER);
        } catch (DateTimeParseException exception) {
            throw BizException.badRequest("VALIDATION_ERROR", "入职日期格式不正确", "hireDate");
        }
        if (hireDate.isAfter(LocalDate.now(ZONE))) {
            throw BizException.badRequest("VALIDATION_ERROR", "入职日期不能晚于当前日期", "hireDate");
        }
        return hireDate;
    }

    private BizException duplicateEmployeeNo() {
        return new BizException(org.springframework.http.HttpStatus.CONFLICT,
                "EMPLOYEE_NO_DUPLICATED", "工号已存在",
                List.of(new FieldErrorItem("employeeNo", "工号已存在")));
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZONE).truncatedTo(ChronoUnit.SECONDS);
    }
}
