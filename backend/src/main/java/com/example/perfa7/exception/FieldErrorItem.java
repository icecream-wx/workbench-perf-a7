package com.example.perfa7.exception;

/**
 * 字段级校验错误，随统一错误响应一起返回。
 */
public record FieldErrorItem(String field, String message) {
}
