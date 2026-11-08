package com.example.perfa7.domain;

/**
 * 性别取值，未填写时统一为 {@link #UNKNOWN}。
 */
public enum Gender {
    MALE,
    FEMALE,
    UNKNOWN;

    public static Gender fromCode(String code) {
        if (code == null || code.isEmpty()) {
            return UNKNOWN;
        }
        for (Gender gender : values()) {
            if (gender.name().equals(code)) {
                return gender;
            }
        }
        return null;
    }
}
