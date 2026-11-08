package com.example.perfa7.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 统一错误响应结构：{@code code} 为业务错误码，{@code fieldErrors} 仅在字段级校验失败时返回。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(String code, String message, List<FieldErrorItem> fieldErrors) {

    public static ApiError of(String code, String message) {
        return new ApiError(code, message, null);
    }

    public static ApiError withField(String code, String message, String field) {
        return new ApiError(code, message, List.of(new FieldErrorItem(field, message)));
    }
}
