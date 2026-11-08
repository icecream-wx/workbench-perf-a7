package com.example.perfa7.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiError> handleBizException(BizException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(new ApiError(exception.getCode(), exception.getMessage(), exception.getFieldErrors()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception) {
        List<FieldErrorItem> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .map(this::toFieldError)
                .toList();
        String message = fieldErrors.isEmpty() ? "请求参数校验失败" : fieldErrors.get(0).message();
        return ResponseEntity.badRequest()
                .body(new ApiError("VALIDATION_ERROR", message, fieldErrors));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException exception) {
        List<FieldErrorItem> fieldErrors = exception.getConstraintViolations().stream()
                .map(this::toFieldError)
                .toList();
        String message = fieldErrors.isEmpty() ? "请求参数校验失败" : fieldErrors.get(0).message();
        return ResponseEntity.badRequest()
                .body(new ApiError("VALIDATION_ERROR", message, fieldErrors));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        String message = "请求参数格式不正确";
        return ResponseEntity.badRequest()
                .body(ApiError.withField("VALIDATION_ERROR", message, exception.getName()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest()
                .body(ApiError.of("VALIDATION_ERROR", "请求体格式不正确"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException exception) {
        log.warn("data integrity violation", exception);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.withField("EMPLOYEE_NO_DUPLICATED", "工号已存在", "employeeNo"));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResource(NoResourceFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of("NOT_FOUND", "请求的资源不存在"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnknown(Exception exception) {
        log.error("unhandled exception", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of("INTERNAL_ERROR", "服务器内部错误"));
    }

    private FieldErrorItem toFieldError(FieldError fieldError) {
        return new FieldErrorItem(fieldError.getField(), fieldError.getDefaultMessage());
    }

    private FieldErrorItem toFieldError(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath().toString();
        int index = path.lastIndexOf('.');
        String field = index >= 0 ? path.substring(index + 1) : path;
        return new FieldErrorItem(field, violation.getMessage());
    }
}
