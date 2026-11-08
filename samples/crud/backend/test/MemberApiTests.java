package __PACKAGE__;

import __PACKAGE__.domain.Member;
import __PACKAGE__.domain.Gender;
import __PACKAGE__.repository.MemberRepository;
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
class MemberApiTests {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MemberRepository memberRepository;

    @BeforeEach
    void cleanDatabase() {
        memberRepository.deleteAll();
    }

    @Test
    void createMemberSuccess() throws Exception {
        Call call = post(validPayload("EMP001"));

        assertEquals(200, call.status());
        assertEquals(0, call.code());
        assertEquals("ok", call.message());
        JsonNode data = call.data();
        assertTrue(data.path("id").asLong() > 0);
        assertEquals("EMP001", data.path("memberNo").asText());
        assertEquals("张三", data.path("name").asText());
        assertEquals("MALE", data.path("gender").asText());
        assertEquals("13800138000", data.path("phone").asText());
        assertEquals("zhangsan@example.com", data.path("email").asText());
        assertEquals("研发部", data.path("department").asText());
        assertEquals("工程师", data.path("position").asText());
        assertEquals("2024-01-31", data.path("joinDate").asText());
        assertTrue(data.path("createdAt").asText().matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"));
        assertEquals(data.path("createdAt").asText(), data.path("updatedAt").asText());
        assertEquals(1, memberRepository.count());

        Call list = get("/api/members?page=1&size=10");
        assertEquals(1, list.data().path("total").asInt());
        assertEquals(1, list.data().path("items").size());
        assertEquals("EMP001", list.data().path("items").get(0).path("memberNo").asText());
    }

    @Test
    void createMemberTrimsValuesAndDefaultsGender() throws Exception {
        Map<String, Object> payload = validPayload("  EMP002  ");
        payload.put("name", "  李四  ");
        payload.remove("gender");
        payload.put("phone", "   ");
        payload.put("email", "");
        payload.put("department", "   ");

        Call call = post(payload);

        assertEquals(0, call.code());
        assertEquals("EMP002", call.data().path("memberNo").asText());
        assertEquals("李四", call.data().path("name").asText());
        assertEquals("UNKNOWN", call.data().path("gender").asText());
        assertTrue(call.data().path("phone").isNull());
        assertTrue(call.data().path("email").isNull());
        assertTrue(call.data().path("department").isNull());
    }

    @Test
    void createMemberRejectsDuplicateMemberNo() throws Exception {
        assertEquals(0, post(validPayload("EMP001")).code());

        Call duplicate = post(validPayload("  emp001  "));

        assertEquals(400, duplicate.status());
        assertEquals(40901, duplicate.code());
        assertTrue(duplicate.message().contains("成员编号已存在"));
        assertEquals(1, memberRepository.count());
    }

    @Test
    void createMemberRejectsMissingMemberNo() throws Exception {
        Map<String, Object> payload = validPayload("EMP001");
        payload.put("memberNo", "   ");

        Call call = post(payload);

        assertEquals(400, call.status());
        assertEquals(400, call.code());
        assertTrue(call.message().contains("成员编号不能为空"));
        assertEquals(0, memberRepository.count());
    }

    @Test
    void validationFailuresAreRejectedWithoutPersisting() throws Exception {
        assertRejected(validPayload("EMP001"), "memberNo", "E".repeat(33), "成员编号长度不能超过32个字符");
        assertRejected(validPayload("EMP001"), "name", "名".repeat(65), "姓名长度不能超过64个字符");
        assertRejected(validPayload("EMP001"), "phone", "1380013800", "手机号格式不正确");
        assertRejected(validPayload("EMP001"), "email", "abc", "邮箱格式不正确");
        assertRejected(validPayload("EMP001"), "gender", "OTHER", "性别取值不合法");
        assertRejected(validPayload("EMP001"), "joinDate", "2024-13-01", "加入日期格式不正确");
        assertRejected(validPayload("EMP001"), "department", "部".repeat(65), "部门长度不能超过64个字符");
        assertRejected(validPayload("EMP001"), "position", "职".repeat(65), "职位长度不能超过64个字符");
        assertEquals(0, memberRepository.count());
    }

    @Test
    void updateMemberSuccess() throws Exception {
        Call created = post(validPayload("EMP001"));
        long id = created.data().path("id").asLong();
        String createdAt = created.data().path("createdAt").asText();

        Map<String, Object> payload = validPayload("EMP001");
        payload.put("name", "张三丰");
        payload.put("department", "平台部");
        payload.put("gender", "FEMALE");
        Call updated = put(id, payload);

        assertEquals(0, updated.code());
        assertEquals(id, updated.data().path("id").asLong());
        assertEquals("张三丰", updated.data().path("name").asText());
        assertEquals("平台部", updated.data().path("department").asText());
        assertEquals("FEMALE", updated.data().path("gender").asText());
        assertEquals("EMP001", updated.data().path("memberNo").asText());

        LocalDateTime createdTime = LocalDateTime.parse(createdAt, DATE_TIME);
        LocalDateTime updatedTime = LocalDateTime.parse(updated.data().path("updatedAt").asText(), DATE_TIME);
        assertTrue(updatedTime.isAfter(createdTime));
        assertEquals(createdAt, updated.data().path("createdAt").asText());

        Call detail = get("/api/members/" + id);
        assertEquals("张三丰", detail.data().path("name").asText());
        assertEquals("平台部", detail.data().path("department").asText());
        assertEquals(updated.data().path("updatedAt").asText(), detail.data().path("updatedAt").asText());
        assertEquals(1, memberRepository.count());
    }

    @Test
    void updateMemberKeepsOwnMemberNo() throws Exception {
        long id = post(validPayload("EMP001")).data().path("id").asLong();

        Map<String, Object> payload = validPayload("EMP001");
        payload.put("department", "平台部");
        Call updated = put(id, payload);

        assertEquals(0, updated.code());
        assertEquals("EMP001", updated.data().path("memberNo").asText());
        assertEquals("平台部", updated.data().path("department").asText());
    }

    @Test
    void updateMemberRejectsOtherMemberNo() throws Exception {
        long first = post(validPayload("EMP001")).data().path("id").asLong();
        long second = post(validPayload("EMP002")).data().path("id").asLong();
        assertNotEquals(first, second);

        Call conflict = put(first, validPayload("EMP002"));

        assertEquals(400, conflict.status());
        assertEquals(40901, conflict.code());
        assertTrue(conflict.message().contains("成员编号已存在"));
        assertEquals("EMP001", get("/api/members/" + first).data().path("memberNo").asText());
        assertEquals(2, memberRepository.count());
    }

    @Test
    void updateMissingMemberReturnsNotFound() throws Exception {
        Call call = put(999999L, validPayload("EMP999"));

        assertEquals(400, call.status());
        assertEquals(40401, call.code());
        assertTrue(call.message().contains("成员不存在"));
        assertEquals(0, memberRepository.count());
    }

    @Test
    void updateValidationFailureDoesNotPartiallyUpdate() throws Exception {
        Call created = post(validPayload("EMP001"));
        long id = created.data().path("id").asLong();

        Map<String, Object> payload = validPayload("EMP001");
        payload.put("name", "新名字");
        payload.put("phone", "123");
        Call invalid = put(id, payload);

        assertEquals(400, invalid.status());
        assertEquals(400, invalid.code());
        assertTrue(invalid.message().contains("手机号格式不正确"));

        Call detail = get("/api/members/" + id);
        assertEquals("张三", detail.data().path("name").asText());
        assertEquals("13800138000", detail.data().path("phone").asText());
    }

    @Test
    void detailReturnsMemberOrNotFound() throws Exception {
        Call created = post(validPayload("EMP001"));
        long id = created.data().path("id").asLong();

        Call detail = get("/api/members/" + id);
        assertEquals(0, detail.code());
        assertEquals("EMP001", detail.data().path("memberNo").asText());

        Call missing = get("/api/members/123456");
        assertEquals(400, missing.status());
        assertEquals(40401, missing.code());
        assertTrue(missing.message().contains("成员不存在"));
    }

    @Test
    void listPaginatesAndCapsPageSize() throws Exception {
        saveMembers(12);

        Call firstPage = get("/api/members?page=1&size=10");
        assertEquals(0, firstPage.code());
        assertEquals(10, firstPage.data().path("items").size());
        assertEquals(12, firstPage.data().path("total").asInt());

        Call secondPage = get("/api/members?page=2&size=10");
        assertEquals(2, secondPage.data().path("items").size());
        assertEquals(12, secondPage.data().path("total").asInt());

        assertEquals("E012", firstPage.data().path("items").get(0).path("memberNo").asText());

        memberRepository.deleteAll();
        saveMembers(105);
        Call capped = get("/api/members?page=1&size=200");
        assertEquals(100, capped.data().path("items").size());
        assertEquals(105, capped.data().path("total").asInt());
    }

    @Test
    void listFiltersByKeyword() throws Exception {
        saveMember("E1001", "张三丰", Gender.MALE);
        saveMember("E2002", "李四", Gender.FEMALE);

        Call byNo = get("/api/members?keyword=e1001");
        assertEquals(1, byNo.data().path("total").asInt());
        assertEquals("E1001", byNo.data().path("items").get(0).path("memberNo").asText());

        Call byName = get("/api/members?keyword=李");
        assertEquals(1, byName.data().path("total").asInt());
        assertEquals("E2002", byName.data().path("items").get(0).path("memberNo").asText());

        Call blankKeyword = perform(MockMvcRequestBuilders.get("/api/members").param("keyword", "   "));
        assertEquals(2, blankKeyword.data().path("total").asInt());

        Call noMatch = get("/api/members?keyword=不存在");
        assertEquals(0, noMatch.data().path("total").asInt());
        assertEquals(0, noMatch.data().path("items").size());
    }

    @Test
    void negativePagingParametersAreRejected() throws Exception {
        Call pageZero = get("/api/members?page=0");
        assertEquals(400, pageZero.status());
        assertEquals(400, pageZero.code());

        Call sizeZero = get("/api/members?size=0");
        assertEquals(400, sizeZero.status());
        assertEquals(400, sizeZero.code());
    }

    private void assertRejected(Map<String, Object> payload, String field, String value, String expectedMessage)
            throws Exception {
        payload.put(field, value);
        Call call = post(payload);
        assertEquals(400, call.status(), "field=" + field);
        assertEquals(400, call.code(), "field=" + field);
        assertTrue(call.message().contains(expectedMessage),
                "expected message to contain [" + expectedMessage + "] but was [" + call.message() + "]");
        assertEquals(0, memberRepository.count(), "field=" + field);
    }

    private void saveMembers(int total) {
        List<Member> members = new ArrayList<>();
        for (int index = 1; index <= total; index++) {
            members.add(newMember(String.format("E%03d", index), "成员" + index, Gender.UNKNOWN));
        }
        memberRepository.saveAll(members);
    }

    private void saveMember(String memberNo, String name, Gender gender) {
        memberRepository.save(newMember(memberNo, name, gender));
    }

    private Member newMember(String memberNo, String name, Gender gender) {
        Member member = new Member();
        member.setMemberNo(memberNo);
        member.setName(name);
        member.setGender(gender);
        member.setJoinDate(LocalDate.of(2024, 1, 31));
        member.setCreatedAt(LocalDateTime.of(2024, 1, 1, 9, 0, 0));
        member.setUpdatedAt(LocalDateTime.of(2024, 1, 1, 9, 0, 0));
        return member;
    }

    private Map<String, Object> validPayload(String memberNo) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("memberNo", memberNo);
        payload.put("name", "张三");
        payload.put("gender", "MALE");
        payload.put("phone", "13800138000");
        payload.put("email", "zhangsan@example.com");
        payload.put("department", "研发部");
        payload.put("position", "工程师");
        payload.put("joinDate", "2024-01-31");
        return payload;
    }

    private Call post(Object payload) throws Exception {
        return perform(MockMvcRequestBuilders.post("/api/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(serialize(payload)));
    }

    private Call put(long id, Object payload) throws Exception {
        return perform(MockMvcRequestBuilders.put("/api/members/" + id)
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

        int code() {
            return body.path("code").asInt(-1);
        }

        String message() {
            return body.path("message").asText();
        }

        JsonNode data() {
            return body.path("data");
        }
    }
}
