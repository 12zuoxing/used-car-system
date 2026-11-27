package cn.edu.usedcar.controller;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import cn.edu.usedcar.service.NotificationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 通过实际 HTTP 过滤器、会话和数据库验证第一模块。测试不模拟权限判断。
 * @author pangzhihao
 */
@SpringBootTest(properties = {
        "springdoc.api-docs.enabled=true",
        "springdoc.swagger-ui.enabled=true",
        "spring.datasource.url=${AUTH_TEST_DB_URL:jdbc:h2:mem:auth-module;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1}",
        "spring.datasource.username=${AUTH_TEST_DB_USER:sa}",
        "spring.datasource.password=${AUTH_TEST_DB_PASSWORD:}",
        "app.admin.username=test_admin",
        "app.admin.password=local-test-password",
        "app.admin.display-name=测试管理员"
})
@AutoConfigureMockMvc
class AuthIntegrationTest {
    private static final String TEST_PASSWORD = "local-test-password";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private TransactionTemplate transactions;
    @Autowired
    private NotificationService notifications;

    @Test
    void openApiDescribesSessionAndCsrfWithoutOpeningProtectedEndpoints() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.paths['/api/auth/login'].post.parameters[0].name").value("X-CSRF-TOKEN"))
                .andExpect(jsonPath("$.paths['/api/auth/register'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/auth/register'].post.responses['409']").exists())
                .andExpect(jsonPath("$.paths['/api/auth/logout'].post").exists())
                .andExpect(jsonPath("$.paths['/api/admin/users'].get.security[0].sessionCookie").isArray())
                .andExpect(jsonPath("$.components.securitySchemes.sessionCookie.in").value("cookie"));
        mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        mvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
        send(post("/api/auth/logout"), csrf(null), null).andExpect(status().isNoContent());
    }

    @Test
    void paginatesAccountsBeforeJoiningTheirRoles() throws Exception {
        String username = newUsername();
        long id = read(register(username).andExpect(status().isCreated())).get("id").asLong();
        MockHttpSession admin = login("test_admin");
        send(put("/api/admin/users/" + id + "/roles"), csrf(admin), "{\"roles\":[\"USER\",\"STAFF\"]}")
                .andExpect(status().isOk());
        long currentPage = jdbc.queryForObject("SELECT COUNT(*) FROM app_user WHERE id <= ?", Long.class, id);
        mvc.perform(get("/api/admin/users").session(admin).param("page", Long.toString(currentPage)).param("pageSize", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].username").value(username))
                .andExpect(jsonPath("$.items[0].roles.length()").value(2));
    }

    @Test
    void registersWithUserRoleAndStoresOnlyBcryptHash() throws Exception {
        String username = newUsername();
        register(username).andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.roles[0]").value("USER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        String hash = jdbc.queryForObject(
                "SELECT password_hash FROM app_user WHERE username = ?", String.class, username);
        assertThat(hash).startsWith("$2").isNotEqualTo(TEST_PASSWORD);
    }

    @Test
    void rejectsDuplicateNamesAndPublicRoleAssignment() throws Exception {
        String username = newUsername();
        register(username).andExpect(status().isCreated());
        register(username.toUpperCase(java.util.Locale.ROOT)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USERNAME_TAKEN"));

        String payload = json.createObjectNode().put("username", newUsername())
                .put("password", TEST_PASSWORD).put("displayName", "非法字段测试")
                .put("role", "ADMIN").toString();
        send(post("/api/auth/register"), csrf(null), payload).andExpect(status().isBadRequest());
    }

    @Test
    void loginRotatesSessionIdAndCsrfTokenThenLogoutInvalidatesSession() throws Exception {
        String username = newUsername();
        register(username).andExpect(status().isCreated());
        Csrf initial = csrf(null);
        String anonymousSessionId = initial.session().getId();
        MockHttpSession session = send(post("/api/auth/login"), initial, credentials(username, TEST_PASSWORD))
                .andExpect(status().isOk()).andExpect(jsonPath("$.username").value(username))
                .andReturn().getRequest().getSession(false) instanceof MockHttpSession loggedIn ? loggedIn : null;
        assertThat(session).isNotNull();
        assertThat(session.getId()).isNotEqualTo(anonymousSessionId);

        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("USER"));
        send(post("/api/auth/logout"), initial, null).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        send(post("/api/auth/logout"), csrf(session), null).andExpect(status().isNoContent());
        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousAndOrdinaryUsersCannotAccessAdminEndpoints() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(credentials("any_user", TEST_PASSWORD))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));

        String username = newUsername();
        register(username).andExpect(status().isCreated());
        MockHttpSession member = login(username);
        mvc.perform(get("/api/admin/users").session(member)).andExpect(status().isForbidden());
        send(put("/api/admin/users/1/roles"), csrf(member), "{\"roles\":[\"ADMIN\"]}")
                .andExpect(status().isForbidden());
    }

    @Test
    void existingSessionsSeeRoleChangesAndDisabledAccountsLoseAccess() throws Exception {
        String username = newUsername();
        long id = read(register(username).andExpect(status().isCreated())).get("id").asLong();
        MockHttpSession member = login(username);
        MockHttpSession admin = login("test_admin");
        String userPath = "/api/admin/users/" + id;

        send(put(userPath + "/roles"), csrf(admin), "{\"roles\":[\"USER\",\"ADMIN\"]}")
                .andExpect(status().isOk());
        mvc.perform(get("/api/admin/users").session(member)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());

        send(put(userPath + "/roles"), csrf(admin), "{\"roles\":[\"USER\"]}")
                .andExpect(status().isOk());
        mvc.perform(get("/api/admin/users").session(member)).andExpect(status().isForbidden());
        send(put(userPath + "/active"), csrf(admin), "{\"active\":false}").andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").session(member)).andExpect(status().isUnauthorized());
        assertThat(member.isInvalid()).isTrue();

        send(post("/api/auth/login"), csrf(null), credentials(username, TEST_PASSWORD))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE subject_type = 'USER' AND subject_id = ?", Long.class, id))
                .isEqualTo(3L);
    }

    @Test
    void reactivationDoesNotRestoreARevokedSession() throws Exception {
        String username = newUsername();
        long id = read(register(username).andExpect(status().isCreated())).get("id").asLong();
        MockHttpSession member = login(username);
        MockHttpSession admin = login("test_admin");
        String path = "/api/admin/users/" + id + "/active";

        // 停用期间保留用户旧会话且不发请求，验证重新启用也不能接受旧凭据。
        send(put(path), csrf(admin), "{\"active\":false}").andExpect(status().isOk());
        send(put(path), csrf(admin), "{\"active\":true}").andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").session(member)).andExpect(status().isUnauthorized());
        assertThat(member.isInvalid()).isTrue();
        login(username);
    }

    @Test
    void protectsTheLastEnabledAdministrator() throws Exception {
        MockHttpSession admin = login("test_admin");
        long id = read(mvc.perform(get("/api/auth/me").session(admin))).get("id").asLong();
        assertThat(id).isPositive();

        send(put("/api/admin/users/" + id + "/active"), csrf(admin), "{\"active\":false}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LAST_ADMIN"));
        send(put("/api/admin/users/" + id + "/roles"), csrf(admin), "{\"roles\":[\"USER\"]}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LAST_ADMIN"));
        mvc.perform(get("/api/admin/users").session(admin)).andExpect(status().isOk());
    }

    @Test
    void validatesPasswordBytesAndKeepsLoginErrorsGeneric() throws Exception {
        String longPassword = "车".repeat(25);
        String payload = json.createObjectNode().put("username", newUsername())
                .put("password", longPassword).put("displayName", "字符验证").toString();
        send(post("/api/auth/register"), csrf(null), payload).andExpect(status().isBadRequest());
        send(post("/api/auth/login"), csrf(null), credentials(newUsername(), longPassword))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void sharedNotificationWriterDeduplicatesAndRollsBackWithBusinessWork() throws Exception {
        long id = read(register(newUsername()).andExpect(status().isCreated())).get("id").asLong();
        assertThatThrownBy(() -> notifications.record(id, null, "test", "USER", id, "首次消息"))
                .isInstanceOf(IllegalTransactionStateException.class);

        transactions.executeWithoutResult(status -> {
            notifications.record(id, null, "test", "USER", id, "首次消息");
            notifications.record(id, null, "test", "USER", id, "重试消息");
        });
        transactions.executeWithoutResult(status -> {
            notifications.record(id, null, "rolled-back", "USER", id, "随事务回滚");
            status.setRollbackOnly();
        });
        assertThat(jdbc.queryForList("SELECT message FROM notification WHERE receiver_id = ?", String.class, id))
                .containsExactly("首次消息");
    }

    private MockHttpSession login(String username) throws Exception {
        return (MockHttpSession) send(post("/api/auth/login"), csrf(null), credentials(username, TEST_PASSWORD))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }

    private ResultActions register(String username) throws Exception {
        String payload = json.createObjectNode().put("username", username).put("password", TEST_PASSWORD)
                .put("displayName", "测试用户").toString();
        return send(post("/api/auth/register"), csrf(null), payload);
    }

    private String credentials(String username, String password) {
        return json.createObjectNode().put("username", username).put("password", password).toString();
    }

    /** 先从实际 CSRF 端点获取表单令牌，避免测试工具绕开会话策略。 */
    private Csrf csrf(MockHttpSession current) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/auth/csrf");
        if (current != null) {
            request.session(current);
        }
        var result = mvc.perform(request).andExpect(status().isOk()).andReturn();
        JsonNode body = json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        return new Csrf((MockHttpSession) result.getRequest().getSession(false),
                body.get("headerName").asText(), body.get("token").asText());
    }

    private ResultActions send(MockHttpServletRequestBuilder request, Csrf csrf, String payload) throws Exception {
        request.session(csrf.session()).header(csrf.header(), csrf.token());
        if (payload != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(payload);
        }
        return mvc.perform(request);
    }

    private JsonNode read(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private String newUsername() {
        return "user_" + UUID.randomUUID().toString().replace("-", "").substring(0, 13);
    }

    private record Csrf(MockHttpSession session, String header, String token) {
    }
}
