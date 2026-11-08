package com.example.perfa7.domain;

/**
 * 员工在职状态，新增时未指定则默认为 {@link #ACTIVE}。
 */
public enum EmployeeStatus {
    ACTIVE,
    INACTIVE;

    public static EmployeeStatus fromCode(String code) {
        if (code == null || code.isEmpty()) {
            return null;
        }
        for (EmployeeStatus status : values()) {
            if (status.name().equals(code)) {
                return status;
            }
        }
        return null;
    }
}
