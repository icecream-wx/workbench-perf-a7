package com.example.perfa7.controller;

import com.example.perfa7.domain.Employee;
import com.example.perfa7.domain.EmployeeStatus;
import com.example.perfa7.exception.BizException;
import com.example.perfa7.service.EmployeeService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/employees")
public class EmployeeController {

    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @GetMapping
    public EmployeePageResponse list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String status) {
        if (page < 1) {
            throw BizException.badRequest("INVALID_PAGINATION", "页码不能小于1", "page");
        }
        if (size < 1) {
            throw BizException.badRequest("INVALID_PAGINATION", "每页条数不能小于1", "size");
        }
        if (size > EmployeeService.MAX_PAGE_SIZE) {
            throw BizException.badRequest("INVALID_PAGINATION",
                    "每页条数不能超过" + EmployeeService.MAX_PAGE_SIZE, "size");
        }
        EmployeeStatus statusFilter = parseStatus(status);
        Page<Employee> result = employeeService.list(keyword, department, statusFilter, page, size);
        List<EmployeeResponse> items = result.getContent().stream()
                .map(EmployeeResponse::from)
                .toList();
        return new EmployeePageResponse(items, result.getTotalElements(), page, size);
    }

    @GetMapping("/{id}")
    public EmployeeResponse detail(@PathVariable long id) {
        return EmployeeResponse.from(employeeService.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EmployeeResponse create(@Valid @RequestBody EmployeeRequest request) {
        return EmployeeResponse.from(employeeService.create(request));
    }

    @PutMapping("/{id}")
    public EmployeeResponse update(@PathVariable long id, @Valid @RequestBody EmployeeRequest request) {
        return EmployeeResponse.from(employeeService.update(id, request));
    }

    private EmployeeStatus parseStatus(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        EmployeeStatus status = EmployeeStatus.fromCode(value.trim());
        if (status == null) {
            throw BizException.badRequest("VALIDATION_ERROR", "状态取值不合法", "status");
        }
        return status;
    }
}
