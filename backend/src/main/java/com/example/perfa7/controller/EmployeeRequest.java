package com.example.perfa7.controller;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 员工新增／编辑请求体。
 *
 * <p>所有字符串字段在反序列化阶段统一去除首尾空白，空白字符串按“未填写”落库为 {@code null}；
 * 因此后续 Bean Validation 校验的是去除空白后的值。</p>
 */
public class EmployeeRequest {

    @NotBlank(message = "工号不能为空")
    @Size(max = 32, message = "工号长度不能超过32个字符")
    private String employeeNo;

    @NotBlank(message = "姓名不能为空")
    @Size(max = 64, message = "姓名长度不能超过64个字符")
    private String name;

    @Pattern(regexp = "MALE|FEMALE|UNKNOWN", message = "性别取值不合法")
    private String gender;

    @Pattern(regexp = "1\\d{10}", message = "手机号格式不正确")
    private String phone;

    @Email(message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱格式不正确")
    private String email;

    @Size(max = 64, message = "部门长度不能超过64个字符")
    private String department;

    @Size(max = 64, message = "职位长度不能超过64个字符")
    private String position;

    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "入职日期格式不正确")
    private String hireDate;

    @Pattern(regexp = "ACTIVE|INACTIVE", message = "状态取值不合法")
    private String status;

    public String getEmployeeNo() {
        return employeeNo;
    }

    public void setEmployeeNo(String employeeNo) {
        this.employeeNo = normalize(employeeNo);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = normalize(name);
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = normalize(gender);
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = normalize(phone);
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = normalize(email);
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = normalize(department);
    }

    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        this.position = normalize(position);
    }

    public String getHireDate() {
        return hireDate;
    }

    public void setHireDate(String hireDate) {
        this.hireDate = normalize(hireDate);
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = normalize(status);
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        int start = 0;
        int end = value.length();
        while (start < end && isBlank(value.charAt(start))) {
            start++;
        }
        while (end > start && isBlank(value.charAt(end - 1))) {
            end--;
        }
        return start == end ? null : value.substring(start, end);
    }

    private static boolean isBlank(char value) {
        return Character.isWhitespace(value) || Character.isSpaceChar(value);
    }
}
