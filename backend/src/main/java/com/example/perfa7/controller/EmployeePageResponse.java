package com.example.perfa7.controller;

import java.util.List;

public record EmployeePageResponse(List<EmployeeResponse> items, long total, int page, int size) {
}
