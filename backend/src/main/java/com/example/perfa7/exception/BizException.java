package com.example.perfa7.exception;

import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * 业务异常：携带 HTTP 状态、业务错误码与可选的字段级错误。
 */
public class BizException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final List<FieldErrorItem> fieldErrors;

    public BizException(HttpStatus status, String code, String message) {
        this(status, code, message, null);
    }

    public BizException(HttpStatus status, String code, String message, List<FieldErrorItem> fieldErrors) {
        super(message);
        this.status = status;
        this.code = code;
        this.fieldErrors = fieldErrors;
    }

    public static BizException badRequest(String code, String message) {
        return new BizException(HttpStatus.BAD_REQUEST, code, message);
    }

    public static BizException badRequest(String code, String message, String field) {
        return new BizException(HttpStatus.BAD_REQUEST, code, message,
                List.of(new FieldErrorItem(field, message)));
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public List<FieldErrorItem> getFieldErrors() {
        return fieldErrors;
    }
}
