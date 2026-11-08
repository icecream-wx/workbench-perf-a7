package com.example.perfa7;

import com.example.perfa7.domain.Employee;
import com.example.perfa7.domain.EmployeeStatus;
import com.example.perfa7.domain.Gender;
import com.example.perfa7.repository.EmployeeRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EmployeeApiTests {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EmployeeRepository employeeRepository;

    @BeforeEach
    void cleanDatabase() {
        employeeRepository.deleteAll();
    }

    @Test
    void createEmployeeSuccess() throws Exception {
        Call call = post(validPayload("EMP001"));

        assertEquals(201, call.status());
        JsonNode body = call.body();
        assertTrue(body.path("id").asLong() > 0);
        assertEquals("EMP001", body.path("employeeNo").asText());
        assertEquals("张三", body.path("name").asText());
        assertEquals("MALE", body.path("gender").asText());
        assertEquals("13800138000", body.path("phone").asText());
        assertEquals("zhangsan@example.com", body.path("email").asText());
        assertEquals("研发部", body.path("department").asText());
        assertEquals("工程师", body.path("position").asText());
        assertEquals("2024-01-31", body.path("hireDate").asText());
        assertEquals("ACTIVE", body.path("status").asText());
        assertTrue(body.path("createdAt").asText().matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"));
        assertEquals(body.path("createdAt").asText(), body.path("updatedAt").asText());
        assertEquals(1, employeeRepository.count());

        Call list = get("/api/employees?page=1&size=10");
        assertEquals(1, list.body().path("total").asInt());
        assertEquals(1, list.body().path("page").asInt());
        assertEquals(10, list.body().path("size").asInt());
        assertEquals(1, list.body().path("items").size());
        assertEquals("EMP001", list.body().path("items").get(0).path("employeeNo").asText());
    }

    @Test
    void createEmployeeTrimsValuesAndDefaultsGenderAndStatus() throws Exception {
        Map<String, Object> payload = validPayload("  EMP002  ");
        payload.put("name", "  李四  ");
        payload.remove("gender");
        payload.remove("status");
        payload.put("phone", "   ");
        payload.put("email", "");
        payload.put("department", "   ");

        Call call = post(payload);

        assertEquals(201, call.status());
        assertEquals("EMP002", call.body().path("employeeNo").asText());
        assertEquals("李四", call.body().path("name").asText());
        assertEquals("UNKNOWN", call.body().path("gender").asText());
        assertEquals("ACTIVE", call.body().path("status").asText());
        assertTrue(call.body().path("phone").isNull());
        assertTrue(call.body().path("email").isNull());
        assertTrue(call.body().path("department").isNull());
    }

    @Test
    void createEmployeeRejectsDuplicateEmployeeNo() throws Exception {
        assertEquals(201, post(validPayload("EMP001")).status());

        Call duplicate = post(validPayload("  emp001  "));

        assertEquals(409, duplicate.status());
        assertEquals("EMPLOYEE_NO_DUPLICATED", duplicate.code());
        assertTrue(duplicate.message().contains("工号已存在"));
        assertEquals("employeeNo", duplicate.fieldErrors().get(0).path("field").asText());
        assertEquals(1, employeeRepository.count());
    }

    @Test
    void createEmployeeRejectsMissingEmployeeNo() throws Exception {
        Map<String, Object> payload = validPayload("EMP001");
        payload.put("employeeNo", "   ");

        Call call = post(payload);

        assertEquals(400, call.status());
        assertEquals("VALIDATION_ERROR", call.code());
        assertTrue(call.message().contains("工号不能为空"));
        assertTrue(hasFieldError(call, "employeeNo"));
        assertEquals(0, employeeRepository.count());
    }

    @Test
    void validationFailuresAreRejectedWithoutPersisting() throws Exception {
        assertRejected(validPayload("EMP001"), "employeeNo", "E".repeat(33), "工号长度不能超过32个字符");
        assertRejected(validPayload("EMP001"), "name", "名".repeat(65), "姓名长度不能超过64个字符");
        assertRejected(validPayload("EMP001"), "phone", "1380013800", "手机号格式不正确");
        assertRejected(validPayload("EMP001"), "email", "abc", "邮箱格式不正确");
        assertRejected(validPayload("EMP001"), "gender", "OTHER", "性别取值不合法");
        assertRejected(validPayload("EMP001"), "hireDate", "2024-13-01", "入职日期格式不正确");
        assertRejected(validPayload("EMP001"), "hireDate",
                LocalDate.now(ZONE).plusDays(1).toString(), "入职日期不能晚于当前日期");
        assertRejected(validPayload("EMP001"), "status", "OTHER", "状态取值不合法");
        assertRejected(validPayload("EMP001"), "department", "部".repeat(65), "部门长度不能超过64个字符");
        assertRejected(validPayload("EMP001"), "position", "职".repeat(65), "职位长度不能超过64个字符");
        assertEquals(0, employeeRepository.count());
    }

    @Test
    void updateEmployeeSuccess() throws Exception {
        Call created = post(validPayload("EMP001"));
        long id = created.body().path("id").asLong();
        String createdAt = created.body().path("createdAt").asText();

        Map<String, Object> payload = validPayload("EMP001");
        payload.put("name", "张三丰");
        payload.put("department", "平台部");
        payload.put("gender", "FEMALE");
        payload.put("status", "INACTIVE");
        Call updated = put(id, payload);

        assertEquals(200, updated.status());
        assertEquals(id, updated.body().path("id").asLong());
        assertEquals("张三丰", updated.body().path("name").asText());
        assertEquals("平台部", updated.body().path("department").asText());
        assertEquals("FEMALE", updated.body().path("gender").asText());
        assertEquals("INACTIVE", updated.body().path("status").asText());
        assertEquals("EMP001", updated.body().path("employeeNo").asText());

        LocalDateTime createdTime = LocalDateTime.parse(createdAt, DATE_TIME);
        LocalDateTime updatedTime = LocalDateTime.parse(updated.body().path("updatedAt").asText(), DATE_TIME);
        assertTrue(updatedTime.isAfter(createdTime));
        assertEquals(createdAt, updated.body().path("createdAt").asText());

        Call detail = get("/api/employees/" + id);
        assertEquals("张三丰", detail.body().path("name").asText());
        assertEquals("平台部", detail.body().path("department").asText());
        assertEquals("INACTIVE", detail.body().path("status").asText());
        assertEquals(updated.body().path("updatedAt").asText(), detail.body().path("updatedAt").asText());
        assertEquals(1, employeeRepository.count());
    }

    @Test
    void updateEmployeeKeepsOwnEmployeeNo() throws Exception {
        long id = post(validPayload("EMP001")).body().path("id").asLong();

        Map<String, Object> payload = validPayload("EMP001");
        payload.put("department", "平台部");
        Call updated = put(id, payload);

        assertEquals(200, updated.status());
        assertEquals("EMP001", updated.body().path("employeeNo").asText());
        assertEquals("平台部", updated.body().path("department").asText());
    }

    @Test
    void updateEmployeeRejectsOtherEmployeeNo() throws Exception {
        long first = post(validPayload("EMP001")).body().path("id").asLong();
        long second = post(validPayload("EMP002")).body().path("id").asLong();
        assertNotEquals(first, second);

        Call conflict = put(first, validPayload("EMP002"));

        assertEquals(409, conflict.status());
        assertEquals("EMPLOYEE_NO_DUPLICATED", conflict.code());
        assertTrue(conflict.message().contains("工号已存在"));
        assertTrue(hasFieldError(conflict, "employeeNo"));
        assertEquals("EMP001", get("/api/employees/" + first).body().path("employeeNo").asText());
        assertEquals(2, employeeRepository.count());
    }

    @Test
    void updateMissingEmployeeReturnsNotFound() throws Exception {
        Call call = put(999999L, validPayload("EMP999"));

        assertEquals(404, call.status());
        assertEquals("EMPLOYEE_NOT_FOUND", call.code());
        assertTrue(call.message().contains("员工不存在"));
        assertEquals(0, employeeRepository.count());
    }

    @Test
    void updateValidationFailureDoesNotPartiallyUpdate() throws Exception {
        Call created = post(validPayload("EMP001"));
        long id = created.body().path("id").asLong();

        Map<String, Object> payload = validPayload("EMP001");
        payload.put("name", "新名字");
        payload.put("phone", "123");
        Call invalid = put(id, payload);

        assertEquals(400, invalid.status());
        assertEquals("VALIDATION_ERROR", invalid.code());
        assertTrue(invalid.message().contains("手机号格式不正确"));

        Call detail = get("/api/employees/" + id);
        assertEquals("张三", detail.body().path("name").asText());
        assertEquals("13800138000", detail.body().path("phone").asText());
    }

    @Test
    void updateRequiresStatus() throws Exception {
        long id = post(validPayload("EMP001")).body().path("id").asLong();
        Map<String, Object> payload = validPayload("EMP001");
        payload.remove("status");

        Call invalid = put(id, payload);

        assertEquals(400, invalid.status());
        assertTrue(hasFieldError(invalid, "status"));
        assertEquals("ACTIVE", get("/api/employees/" + id).body().path("status").asText());
    }

    @Test
    void detailReturnsEmployeeOrNotFound() throws Exception {
        Call created = post(validPayload("EMP001"));
        long id = created.body().path("id").asLong();

        Call detail = get("/api/employees/" + id);
        assertEquals(200, detail.status());
        assertEquals("EMP001", detail.body().path("employeeNo").asText());

        Call missing = get("/api/employees/123456");
        assertEquals(404, missing.status());
        assertEquals("EMPLOYEE_NOT_FOUND", missing.code());
        assertTrue(missing.message().contains("员工不存在"));
    }

    @Test
    void listPaginatesWithStableOrder() throws Exception {
        saveEmployees(12);

        Call firstPage = get("/api/employees?page=1&size=10");
        assertEquals(200, firstPage.status());
        assertEquals(10, firstPage.body().path("items").size());
        assertEquals(12, firstPage.body().path("total").asInt());
        assertEquals("E001", firstPage.body().path("items").get(0).path("employeeNo").asText());

        Call secondPage = get("/api/employees?page=2&size=10");
        assertEquals(2, secondPage.body().path("items").size());
        assertEquals(12, secondPage.body().path("total").asInt());
        assertEquals("E011", secondPage.body().path("items").get(0).path("employeeNo").asText());
    }

    @Test
    void listFiltersByKeyword() throws Exception {
        saveEmployee("E1001", "张三丰", Gender.MALE, "研发部", EmployeeStatus.ACTIVE);
        saveEmployee("E2002", "李四", Gender.FEMALE, "财务部", EmployeeStatus.ACTIVE);

        Call byNo = get("/api/employees?keyword=e1001");
        assertEquals(1, byNo.body().path("total").asInt());
        assertEquals("E1001", byNo.body().path("items").get(0).path("employeeNo").asText());

        Call byName = get("/api/employees?keyword=李");
        assertEquals(1, byName.body().path("total").asInt());
        assertEquals("E2002", byName.body().path("items").get(0).path("employeeNo").asText());

        Call blankKeyword = perform(MockMvcRequestBuilders.get("/api/employees").param("keyword", "   "));
        assertEquals(2, blankKeyword.body().path("total").asInt());

        Call noMatch = get("/api/employees?keyword=不存在");
        assertEquals(0, noMatch.body().path("total").asInt());
        assertEquals(0, noMatch.body().path("items").size());
    }

    @Test
    void listFiltersByDepartmentAndStatus() throws Exception {
        saveEmployee("E1001", "张三丰", Gender.MALE, "研发部", EmployeeStatus.ACTIVE);
        saveEmployee("E1002", "李四", Gender.FEMALE, "研发部", EmployeeStatus.INACTIVE);
        saveEmployee("E1003", "王五", Gender.UNKNOWN, "财务部", EmployeeStatus.ACTIVE);

        Call byDepartment = perform(MockMvcRequestBuilders.get("/api/employees")
                .param("department", "研发部"));
        assertEquals(2, byDepartment.body().path("total").asInt());

        Call byStatus = get("/api/employees?status=INACTIVE");
        assertEquals(1, byStatus.body().path("total").asInt());
        assertEquals("E1002", byStatus.body().path("items").get(0).path("employeeNo").asText());

        Call combined = perform(MockMvcRequestBuilders.get("/api/employees")
                .param("department", "研发部")
                .param("status", "ACTIVE"));
        assertEquals(1, combined.body().path("total").asInt());
        assertEquals("E1001", combined.body().path("items").get(0).path("employeeNo").asText());
    }

    @Test
    void listRejectsInvalidStatus() throws Exception {
        Call invalid = get("/api/employees?status=UNKNOWN");

        assertEquals(400, invalid.status());
        assertEquals("VALIDATION_ERROR", invalid.code());
        assertTrue(hasFieldError(invalid, "status"));
    }

    @Test
    void pagingParametersAreValidated() throws Exception {
        Call pageZero = get("/api/employees?page=0");
        assertEquals(400, pageZero.status());
        assertEquals("INVALID_PAGINATION", pageZero.code());

        Call sizeZero = get("/api/employees?size=0");
        assertEquals(400, sizeZero.status());
        assertEquals("INVALID_PAGINATION", sizeZero.code());

        Call sizeTooLarge = get("/api/employees?size=101");
        assertEquals(400, sizeTooLarge.status());
        assertEquals("INVALID_PAGINATION", sizeTooLarge.code());

        Call sizeMax = get("/api/employees?size=100");
        assertEquals(200, sizeMax.status());
        assertEquals(100, sizeMax.body().path("size").asInt());
    }

    private void assertRejected(Map<String, Object> payload, String field, String value, String expectedMessage)
            throws Exception {
        payload.put(field, value);
        Call call = post(payload);
        assertEquals(400, call.status(), "field=" + field);
        assertEquals("VALIDATION_ERROR", call.code(), "field=" + field);
        assertTrue(call.message().contains(expectedMessage),
                "expected message to contain [" + expectedMessage + "] but was [" + call.message() + "]");
        assertTrue(hasFieldError(call, field), "field=" + field);
        assertEquals(0, employeeRepository.count(), "field=" + field);
    }

    private boolean hasFieldError(Call call, String field) {
        JsonNode errors = call.fieldErrors();
        if (!errors.isArray()) {
            return false;
        }
        for (JsonNode item : errors) {
            if (field.equals(item.path("field").asText())) {
                return true;
            }
        }
        return false;
    }

    private void saveEmployees(int total) {
        List<Employee> employees = new ArrayList<>();
        for (int index = 1; index <= total; index++) {
            employees.add(newEmployee(String.format("E%03d", index), "员工" + index,
                    Gender.UNKNOWN, "研发部", EmployeeStatus.ACTIVE));
        }
        employeeRepository.saveAll(employees);
    }

    private void saveEmployee(String employeeNo, String name, Gender gender,
                              String department, EmployeeStatus status) {
        employeeRepository.save(newEmployee(employeeNo, name, gender, department, status));
    }

    private Employee newEmployee(String employeeNo, String name, Gender gender,
                                 String department, EmployeeStatus status) {
        Employee employee = new Employee();
        employee.setEmployeeNo(employeeNo);
        employee.setName(name);
        employee.setGender(gender);
        employee.setDepartment(department);
        employee.setStatus(status);
        employee.setHireDate(LocalDate.of(2024, 1, 31));
        employee.setCreatedAt(LocalDateTime.of(2024, 1, 1, 9, 0, 0));
        employee.setUpdatedAt(LocalDateTime.of(2024, 1, 1, 9, 0, 0));
        return employee;
    }

    private Map<String, Object> validPayload(String employeeNo) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("employeeNo", employeeNo);
        payload.put("name", "张三");
        payload.put("gender", "MALE");
        payload.put("phone", "13800138000");
        payload.put("email", "zhangsan@example.com");
        payload.put("department", "研发部");
        payload.put("position", "工程师");
        payload.put("hireDate", "2024-01-31");
        payload.put("status", "ACTIVE");
        return payload;
    }

    private Call post(Object payload) throws Exception {
        return perform(MockMvcRequestBuilders.post("/api/employees")
                .contentType(MediaType.APPLICATION_JSON)
                .content(serialize(payload)));
    }

    private Call put(long id, Object payload) throws Exception {
        return perform(MockMvcRequestBuilders.put("/api/employees/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(serialize(payload)));
    }

    private Call get(String url) throws Exception {
        return perform(MockMvcRequestBuilders.get(url));
    }

    private Call perform(MockHttpServletRequestBuilder builder) throws Exception {
        MvcResult result = mockMvc.perform(builder).andReturn();
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode node = body.isEmpty() ? objectMapper.createObjectNode() : objectMapper.readTree(body);
        return new Call(result.getResponse().getStatus(), node);
    }

    private String serialize(Object payload) throws Exception {
        String json = objectMapper.writeValueAsString(payload);
        assertNotNull(json);
        assertFalse(json.isBlank());
        return json;
    }

    private record Call(int status, JsonNode body) {

        String code() {
            return body.path("code").asText("");
        }

        String message() {
            return body.path("message").asText();
        }

        JsonNode fieldErrors() {
            return body.path("fieldErrors");
        }
    }
}
