package cn.edu.usedcar.controller;

import java.util.Locale;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 从 HTTP 入口检验车源、审核、筛选和字典权限。测试在 H2 或隔离的 MySQL 库运行。
 * @author pangzhihao
 */
@SpringBootTest(properties = {
        "spring.datasource.url=${AUTH_TEST_DB_URL:jdbc:h2:mem:vehicle-module;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1}",
        "spring.datasource.username=${AUTH_TEST_DB_USER:sa}",
        "spring.datasource.password=${AUTH_TEST_DB_PASSWORD:}",
        "app.admin.username=test_admin", "app.admin.password=local-test-password", "app.admin.display-name=测试管理员"
})
@AutoConfigureMockMvc
class VehicleIntegrationTest {
    private static final String PASSWORD = "local-test-password";
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void sellReviewResubmitListAndTakeOffline() throws Exception {
        var admin = login("test_admin");
        var seller = register();
        var outsider = register();
        var staff = register();
        call(put("/api/admin/users/" + staff.id() + "/roles"), admin, "{\"roles\":[\"STAFF\"]}")
                .andExpect(status().isOk());
        long brand = dictionary(admin, "BRAND");
        long city = dictionary(admin, "CITY");
        String payload = vehicle(brand, city, "DIRECT", "初始车型", 2020, "68800.00");
        JsonNode created = read(call(post("/api/vehicles"), seller.session(), payload)
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING")));
        long id = created.get("id").asLong();
        long firstRevision = created.get("revision").asLong();

        mvc.perform(get("/api/vehicles/" + id)).andExpect(status().isNotFound());
        mvc.perform(get("/api/vehicles/mine/" + id)).andExpect(status().isUnauthorized());
        call(put("/api/vehicles/" + id), outsider.session(), payload).andExpect(status().isNotFound());
        call(post("/api/admin/vehicles/" + id + "/review"), seller.session(), review(firstRevision, "APPROVE", ""))
                .andExpect(status().isForbidden());
        call(post("/api/admin/vehicles/" + id + "/review"), staff.session(), review(firstRevision, "REJECT", ""))
                .andExpect(status().isBadRequest());
        call(post("/api/admin/vehicles/" + id + "/review"), staff.session(), review(firstRevision, "REJECT", "补充车辆描述"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reviewNote").value("补充车辆描述"));

        JsonNode revised = read(call(put("/api/vehicles/" + id), seller.session(),
                vehicle(brand, city, "DIRECT", "修正车型", 2020, "68000.00"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING")));
        long newRevision = revised.get("revision").asLong();
        assertThat(newRevision).isGreaterThan(firstRevision);
        call(post("/api/admin/vehicles/" + id + "/review"), staff.session(), review(firstRevision, "APPROVE", ""))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_REVIEW"));
        call(post("/api/admin/vehicles/" + id + "/review"), staff.session(), review(newRevision, "APPROVE", "审核通过"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("LISTED"));
        mvc.perform(get("/api/vehicles/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.model").value("修正车型"))
                .andExpect(jsonPath("$.ownerId").doesNotExist()).andExpect(jsonPath("$.reviewNote").doesNotExist())
                .andExpect(jsonPath("$.revision").doesNotExist());
        mvc.perform(get("/api/vehicles/mine").session(seller.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE vehicle_id = ?", Long.class, id))
                .isEqualTo(2L);
        call(post("/api/vehicles/" + id + "/offline"), outsider.session(), null).andExpect(status().isNotFound());
        call(post("/api/vehicles/" + id + "/offline"), seller.session(), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("OFFLINE"));
        mvc.perform(get("/api/vehicles/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void combinePublicFiltersAndExcludePendingAndConsignmentDrafts() throws Exception {
        var admin = login("test_admin");
        var seller = register();
        long brand = dictionary(admin, "BRAND");
        long city = dictionary(admin, "CITY");
        for (String price : new String[]{"52000.00", "57000.00", "95000.00"}) {
            JsonNode entry = read(call(post("/api/vehicles"), seller.session(), vehicle(brand, city, "DIRECT", price, 2021, price))
                    .andExpect(status().isCreated()));
            call(post("/api/admin/vehicles/" + entry.get("id").asLong() + "/review"), admin,
                    review(entry.get("revision").asLong(), "APPROVE", "")).andExpect(status().isOk());
        }
        JsonNode consignment = read(call(post("/api/vehicles"), seller.session(),
                vehicle(brand, city, "CONSIGN", "委托车源", 2021, "55000.00")).andExpect(status().isCreated()));
        call(post("/api/admin/vehicles/" + consignment.get("id").asLong() + "/review"), admin,
                review(consignment.get("revision").asLong(), "APPROVE", ""))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONSIGNMENT_REQUIRED"));

        mvc.perform(get("/api/vehicles").param("brandId", Long.toString(brand)).param("cityId", Long.toString(city))
                .param("minPrice", "50000").param("maxPrice", "60000").param("minYear", "2020")
                .param("maxMileage", "70000").param("saleMode", "DIRECT").param("pageSize", "1").param("page", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.page").value(2));
        mvc.perform(get("/api/vehicles").param("minPrice", "80000").param("maxPrice", "20000"))
                .andExpect(status().isBadRequest());
        call(post("/api/vehicles"), seller.session(), vehicle(brand, city, "DIRECT", "未来车型", 9999, "50000"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void dictionaryRequiresAdminAndPreservesPublishedVehiclesWhenDisabled() throws Exception {
        var admin = login("test_admin");
        var seller = register();
        var staff = register();
        call(put("/api/admin/users/" + staff.id() + "/roles"), admin, "{\"roles\":[\"STAFF\"]}").andExpect(status().isOk());
        call(post("/api/admin/dictionaries/BRAND"), staff.session(), "{\"name\":\"禁止创建\",\"active\":true}")
                .andExpect(status().isForbidden());
        long city = dictionary(admin, "CITY");
        long brand = dictionary(admin, "BRAND");
        JsonNode vehicle = read(call(post("/api/vehicles"), seller.session(), vehicle(brand, city, "DIRECT", "已发布", 2020, "50000"))
                .andExpect(status().isCreated()));
        long id = vehicle.get("id").asLong();
        call(post("/api/admin/vehicles/" + id + "/review"), admin, review(vehicle.get("revision").asLong(), "APPROVE", ""))
                .andExpect(status().isOk());

        var disabled = "{\"name\":\"已停用品牌\",\"active\":false}";
        call(put("/api/admin/dictionaries/BRAND/" + brand), admin, disabled).andExpect(status().isOk());
        mvc.perform(get("/api/dictionaries")).andExpect(status().isOk())
                .andExpect(jsonPath("$.brands[?(@.id == " + brand + ")]").isEmpty());
        mvc.perform(get("/api/vehicles/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.brandName").value("已停用品牌"));
        call(post("/api/vehicles"), seller.session(), vehicle(brand, city, "DIRECT", "不可提交", 2020, "50000"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DICTIONARY_INACTIVE"));
        call(post("/api/admin/dictionaries/BRAND"), admin, disabled).andExpect(status().isConflict());
    }

    private long dictionary(MockHttpSession admin, String type) throws Exception {
        return read(call(post("/api/admin/dictionaries/" + type), admin,
                json.createObjectNode().put("name", type + "-" + suffix()).put("active", true).toString())
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private String vehicle(long brand, long city, String mode, String model, int year, String price) {
        return json.createObjectNode().put("brandId", brand).put("cityId", city).put("saleMode", mode)
                .put("model", model).put("registrationYear", year).put("price", new java.math.BigDecimal(price))
                .put("mileage", 64000).put("transmission", "AUTO").put("fuel", "PETROL")
                .put("description", "毕业设计测试车源").toString();
    }

    private String review(long revision, String decision, String note) {
        return json.createObjectNode().put("revision", revision).put("decision", decision).put("note", note).toString();
    }

    private TestUser register() throws Exception {
        String user = "vehicle_" + suffix();
        long id = read(call(post("/api/auth/register"), null, json.createObjectNode().put("username", user)
                .put("password", PASSWORD).put("displayName", "车源测试").toString()).andExpect(status().isCreated())).get("id").asLong();
        return new TestUser(id, login(user));
    }

    private MockHttpSession login(String user) throws Exception {
        return (MockHttpSession) call(post("/api/auth/login"), null, json.createObjectNode().put("username", user)
                .put("password", PASSWORD).toString()).andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }

    /** 从真实会话取得 CSRF 令牌，保持请求与前端写操作一致。 */
    private ResultActions call(MockHttpServletRequestBuilder write, MockHttpSession session, String body) throws Exception {
        var csrf = get("/api/auth/csrf");
        if (session != null) {
            csrf.session(session);
        }
        var result = mvc.perform(csrf).andExpect(status().isOk()).andReturn();
        var token = json.readTree(result.getResponse().getContentAsByteArray());
        write.session((MockHttpSession) result.getRequest().getSession(false))
                .header(token.get("headerName").asText(), token.get("token").asText());
        if (body != null) {
            write.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mvc.perform(write);
    }

    private JsonNode read(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsByteArray());
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8).toLowerCase(Locale.ROOT);
    }

    private record TestUser(long id, MockHttpSession session) {
    }
}
