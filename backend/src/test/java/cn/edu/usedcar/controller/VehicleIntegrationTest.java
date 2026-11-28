package cn.edu.usedcar.controller;

import java.util.Locale;
import java.util.UUID;
import java.util.Base64;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
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
    @Autowired private cn.edu.usedcar.service.ConsignmentService consignments; // 直接验证下一模块复用的下单资格入口。
    @Autowired private org.springframework.transaction.PlatformTransactionManager transactions; // 让资格检查使用与下单相同的事务边界。
    @org.junit.jupiter.api.io.TempDir
    static java.nio.file.Path uploadDirectory;

    @DynamicPropertySource
    static void uploads(DynamicPropertyRegistry properties) {
        properties.add("app.uploads", () -> uploadDirectory.toString());
    }

    @Test
    void photosAndReportsHonorVehicleOwnershipAndPublicationState() throws Exception {
        var admin = login("test_admin");
        var seller = register();
        var outsider = register();
        long brand = dictionary(admin, "BRAND");
        long city = dictionary(admin, "CITY");
        var first = read(call(post("/api/vehicles"), seller.session(), vehicle(brand, city, "DIRECT", "照片测试", 2020, "50000"))
                .andExpect(status().isCreated()));
        var second = read(call(post("/api/vehicles"), seller.session(), vehicle(brand, city, "DIRECT", "另一辆车", 2020, "51000"))
                .andExpect(status().isCreated()));
        long id = first.get("id").asLong();
        byte[] png = png();
        var image = new MockMultipartFile("file", "实拍.png", "image/png", png);
        long photoId = read(call(multipart("/api/vehicles/" + id + "/attachments").file(image),
                seller.session(), null).andExpect(status().isCreated())).get("id").asLong();
        mvc.perform(get("/api/attachments/" + photoId)).andExpect(status().isNotFound());
        mvc.perform(get("/api/attachments/" + photoId).session(outsider.session())).andExpect(status().isNotFound());
        mvc.perform(get("/api/attachments/" + photoId).session(seller.session()))
                .andExpect(status().isOk()).andExpect(content().bytes(png));
        byte[] webp = Base64.getDecoder().decode("UklGRjwAAABXRUJQVlA4IDAAAADQAQCdASoEAAQAAUAmJaACdLoB+AADsAD+71Ev/lKvBY3R5/+BO/AnfgTv/CBAAAA=");
        call(multipart("/api/vehicles/" + id + "/attachments")
                .file(new MockMultipartFile("file", "另一张.webp", "image/webp", webp)), seller.session(), null)
                .andExpect(status().isCreated()).andExpect(jsonPath("$.mime").value("image/webp"));
        call(post("/api/admin/vehicles/" + id + "/review"), admin, review(first.get("revision").asLong(), "APPROVE", ""))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_REVIEW"));
        long revision = read(mvc.perform(get("/api/vehicles/mine/" + id).session(seller.session()))).get("revision").asLong();
        call(post("/api/admin/vehicles/" + id + "/review"), admin, review(revision, "APPROVE", ""))
                .andExpect(status().isOk());
        mvc.perform(get("/api/attachments/" + photoId)).andExpect(status().isOk());

        String draft = "{\"source\":\"查博士\",\"reportDate\":\"2024-05-02\",\"summary\":\"检查了外观和车身。\"}";
        long reportId = read(call(post("/api/vehicles/" + id + "/reports"), seller.session(), draft)
                .andExpect(status().isCreated())).get("id").asLong();
        byte[] pdf;
        try (var document = new org.apache.pdfbox.pdmodel.PDDocument();
             var output = new java.io.ByteArrayOutputStream()) {
            document.addPage(new org.apache.pdfbox.pdmodel.PDPage());
            document.save(output);
            pdf = output.toByteArray();
        }
        var file = new MockMultipartFile("file", "检测报告.pdf", "application/pdf", pdf);
        call(multipart("/api/vehicles/" + second.get("id").asLong() + "/attachments").file(file)
                .param("reportId", Long.toString(reportId)), seller.session(), null).andExpect(status().isNotFound());
        long reportFile = read(call(multipart("/api/vehicles/" + id + "/attachments").file(file)
                .param("reportId", Long.toString(reportId)), seller.session(), null)
                .andExpect(status().isCreated())).get("id").asLong();
        mvc.perform(get("/api/vehicles/" + id + "/reports")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/attachments/" + reportFile)).andExpect(status().isNotFound());
        call(post("/api/admin/reports/" + reportId + "/review"), admin, review(0, "APPROVE", ""))
                .andExpect(status().isConflict());
        call(post("/api/admin/reports/" + reportId + "/review"), admin, review(reportFile, "APPROVE", ""))
                .andExpect(status().isOk());
        mvc.perform(get("/api/vehicles/" + id + "/reports")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].source").value("查博士")).andExpect(jsonPath("$[0].authorName").doesNotExist());
        mvc.perform(get("/api/attachments/" + reportFile)).andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment")))
                .andExpect(content().bytes(pdf));
        call(post("/api/vehicles/" + id + "/offline"), seller.session(), null).andExpect(status().isOk());
        mvc.perform(get("/api/attachments/" + reportFile)).andExpect(status().isNotFound());
    }

    @Test
    void rejectsFakeAndOversizedFilesAndRollsBackIncompleteMultipartReports() throws Exception {
        var admin = login("test_admin");
        var seller = register();
        long brand = dictionary(admin, "BRAND");
        long city = dictionary(admin, "CITY");
        long id = read(call(post("/api/vehicles"), seller.session(), vehicle(brand, city, "DIRECT", "拒绝无效附件", 2020, "50000"))
                .andExpect(status().isCreated())).get("id").asLong();
        var forged = new MockMultipartFile("file", "伪装.png", "image/png", "<html>not an image</html>".getBytes());
        call(multipart("/api/vehicles/" + id + "/attachments").file(forged), seller.session(), null)
                .andExpect(status().isUnsupportedMediaType());
        byte[] incompleteJpeg = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xff, (byte) 0xd9};
        call(multipart("/api/vehicles/" + id + "/attachments")
                .file(new MockMultipartFile("file", "损坏.jpg", "image/jpeg", incompleteJpeg)), seller.session(), null)
                .andExpect(status().isUnsupportedMediaType());
        var tooLarge = new MockMultipartFile("file", "过大.png", "image/png", new byte[5 * 1024 * 1024 + 1]);
        call(multipart("/api/vehicles/" + id + "/attachments").file(tooLarge), seller.session(), null)
                .andExpect(status().isPayloadTooLarge());
        var report = new MockMultipartFile("report", "", "application/json",
                "{\"source\":\"查博士\",\"reportDate\":\"2024-05-02\",\"summary\":\"附件失败后不应有记录。\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        call(multipart("/api/vehicles/" + id + "/reports").file(report).file(forged), seller.session(), null)
                .andExpect(status().isUnsupportedMediaType());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM vehicle_report WHERE vehicle_id = ?", Long.class, id)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM attachment WHERE vehicle_id = ?", Long.class, id)).isZero();
    }

    @Test
    void publishesCompleteVehicleMediaOrRollsBackAllOfIt() throws Exception {
        var admin = login("test_admin");
        var seller = register();
        long brand = dictionary(admin, "BRAND");
        long city = dictionary(admin, "CITY");
        var vehicle = new MockMultipartFile("vehicle", "", "application/json",
                vehicle(brand, city, "DIRECT", "原子发布", 2020, "50000").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var report = new MockMultipartFile("report", "", "application/json",
                "{\"source\":\"查博士\",\"reportDate\":\"2024-05-02\",\"summary\":\"外观已检测。\"}"
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var photo = new MockMultipartFile("photo", "有效照片.png", "image/png", png());
        var invalid = new MockMultipartFile("reportFile", "无效.pdf", "application/pdf",
                "%PDF-1.4\n%%EOF\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        long countBefore;
        try (var files = java.nio.file.Files.list(uploadDirectory)) {
            countBefore = files.count();
        }
        call(multipart("/api/vehicles").file(vehicle).file(photo).file(report).file(invalid), seller.session(), null)
                .andExpect(status().isUnsupportedMediaType());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM vehicle WHERE owner_id = ?", Long.class, seller.id()))
                .isZero();
        try (var files = java.nio.file.Files.list(uploadDirectory)) {
            assertThat(files.count()).isEqualTo(countBefore);
        }
        var created = read(call(multipart("/api/vehicles").file(vehicle).file(photo).file(report), seller.session(), null)
                .andExpect(status().isCreated()).andExpect(jsonPath("$.coverPhotoId").isNumber()));
        mvc.perform(get("/api/vehicles/" + created.get("id").asLong() + "/reports").session(seller.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    private static byte[] png() throws Exception {
        var output = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(4, 4, java.awt.image.BufferedImage.TYPE_INT_RGB),
                "png", output);
        return output.toByteArray();
    }

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
    void consignmentRequiresOwnerReviewAndCurrentTermBeforePublication() throws Exception {
        // 管理员负责两次审核，卖家提交委托，买家仅能访问公开资料。
        var admin = login("test_admin");
        var seller = register();
        var buyer = register();
        var brand = dictionary(admin, "BRAND");
        var city = dictionary(admin, "CITY");
        var car = read(call(post("/api/vehicles"), seller.session(), vehicle(brand, city, "CONSIGN", "代销测试", 2022, "55000"))
                .andExpect(status().isCreated()));
        long id = car.get("id").asLong();
        long photo = read(call(multipart("/api/vehicles/" + id + "/attachments")
                .file(new MockMultipartFile("file", "实拍.png", "image/png", png())), seller.session(), null)
                .andExpect(status().isCreated())).get("id").asLong();
        var now = java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai"));
        var draft = json.createObjectNode().put("startsAt", now.plusHours(1).toString()) // 先登记未来才开始的委托。
                .put("expiresAt", now.plusDays(3).toString()).put("fee", 1000).put("terms", "费用从成交价中扣除").toString();
        String submit = "/api/vehicles/" + id + "/consignments";
        call(post(submit), buyer.session(), draft).andExpect(status().isNotFound());
        long agreement = read(call(post(submit), seller.session(), draft).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))).get("id").asLong();
        call(post(submit), seller.session(), draft).andExpect(status().isConflict());
        mvc.perform(get(submit).session(buyer.session())).andExpect(status().isNotFound());
        var approve = "{\"decision\":\"APPROVE\"}";
        String reviewAgreement = "/api/admin/consignments/" + agreement + "/review";
        call(post(reviewAgreement), seller.session(), approve).andExpect(status().isForbidden());
        call(post(reviewAgreement), admin, approve).andExpect(status().isOk());
        call(post(reviewAgreement), admin, approve).andExpect(status().isConflict());
        call(post(submit), seller.session(), draft).andExpect(status().isConflict());
        // 委托和车辆独立审核，委托通过后仍须完成车源审核。
        mvc.perform(get("/api/vehicles/" + id)).andExpect(status().isNotFound());
        String publish = "/api/admin/vehicles/" + id + "/review"; // 保存车源审核路径以便验证两个时间点。
        long revision = read(mvc.perform(get("/api/vehicles/mine/" + id).session(seller.session())))
                .get("revision").asLong(); // 上传新照片后读取最新的车源审核版本。
        String review = review(revision, "APPROVE", ""); // 在同一版本上分别测试未开始与已开始的上架结果。
        call(post(publish), admin, review).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONSIGNMENT_REQUIRED"));
        jdbc.update("UPDATE consignment SET starts_at = ? WHERE id = ?", now.minusHours(1), agreement); // 模拟委托开始时间已到，避免让测试等待。
        call(post(publish), admin, review)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("LISTED"));
        var transaction = new org.springframework.transaction.support.TransactionTemplate(transactions); // 在独立事务模拟订单服务。
        Long orderAgreement = transaction.execute(ignored -> consignments.requireForOrder(id)); // 检查真实下单锁会取得有效委托。
        assertThat(orderAgreement).isEqualTo(agreement); // 交易必须引用本车已审核且有效的委托。
        mvc.perform(get("/api/vehicles/" + id)).andExpect(status().isOk());
        mvc.perform(get("/api/attachments/" + photo)).andExpect(status().isOk());
        var lateVisit = json.createObjectNode().put("visitAt", now.plusDays(4).toString()).toString(); // 预约时间晚于代销截止日期。
        call(post("/api/vehicles/" + id + "/appointments"), buyer.session(), lateVisit).andExpect(status().isBadRequest());
        // 用真实订单行验证即使车辆未被标记锁定，交易中也不能撤回委托。
        jdbc.update("INSERT INTO trade_order (vehicle_id, buyer_id, seller_id, consignment_id, request_key, amount) VALUES (?, ?, ?, ?, ?, ?)",
                id, buyer.id(), seller.id(), agreement, suffix(), 55000);
        String withdraw = "/api/consignments/" + agreement + "/withdraw";
        call(post(withdraw), seller.session(), null).andExpect(status().isConflict());
        jdbc.update("UPDATE trade_order SET status = 'CANCELLED' WHERE vehicle_id = ?", id);
        // 将有效期改为已过去，验证全站读路径都会立即隐藏过期代销。
        jdbc.update("UPDATE consignment SET expires_at = ? WHERE id = ?", now.minusMinutes(10), agreement);
        mvc.perform(get("/api/vehicles/" + id)).andExpect(status().isNotFound());
        mvc.perform(get("/api/attachments/" + photo)).andExpect(status().isNotFound());
        mvc.perform(get("/api/vehicles/" + id + "/attachments")).andExpect(status().isNotFound());
        mvc.perform(get("/api/vehicles").param("brandId", Long.toString(brand))).andExpect(jsonPath("$.total").value(0));
        mvc.perform(get(submit).session(seller.session())).andExpect(jsonPath("$.items[0].status").value("EXPIRED"));
        mvc.perform(get("/api/admin/consignments").param("status", "EXPIRED").session(admin))
                .andExpect(jsonPath("$.items[?(@.id == " + agreement + ")]").isNotEmpty());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> transaction.execute(ignored -> consignments.requireForOrder(id)))
                .isInstanceOf(cn.edu.usedcar.common.ApiException.class).hasMessageContaining("需要当前有效的委托"); // 已过期记录不能用于下单。
        mvc.perform(get("/api/vehicles/mine/" + id).session(seller.session())).andExpect(jsonPath("$.status").value("OFFLINE")); // 个人视图响应自然下架。
        long renewal = read(call(post(submit), seller.session(), draft).andExpect(status().isCreated())).get("id").asLong(); // 到期后允许提交新的申请。
        call(post("/api/consignments/" + renewal + "/withdraw"), buyer.session(), null).andExpect(status().isNotFound()); // 买家不能撤回卖家的委托。
        call(post("/api/consignments/" + renewal + "/withdraw"), seller.session(), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("WITHDRAWN")); // 卖家可撤回自己的待审核委托。
    }

    @Test
    void appointmentEnforcesTimeOwnershipAndHandlingState() throws Exception {
        // 用独立买卖账号验证预约只向本人和工作人员开放。
        var admin = login("test_admin");
        var seller = register();
        var buyer = register();
        var stranger = register();
        var car = read(call(post("/api/vehicles"), seller.session(), vehicle(dictionary(admin, "BRAND"),
                dictionary(admin, "CITY"), "DIRECT", "预约测试", 2022, "55000")).andExpect(status().isCreated()));
        long id = car.get("id").asLong();
        String submit = "/api/vehicles/" + id + "/appointments";
        var now = java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai"));
        var future = json.createObjectNode().put("visitAt", now.plusDays(1).toString()).put("note", "想检查底盘").toString();
        call(post(submit), buyer.session(), future).andExpect(status().isNotFound());
        call(post("/api/admin/vehicles/" + id + "/review"), admin, review(car.get("revision").asLong(), "APPROVE", ""))
                .andExpect(status().isOk());
        call(post(submit), seller.session(), future).andExpect(status().isBadRequest());
        call(post(submit), buyer.session(), json.createObjectNode().put("visitAt", now.minusHours(1).toString()).toString())
                .andExpect(status().isBadRequest());
        long booking = read(call(post(submit), buyer.session(), future).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))).get("id").asLong();
        call(post(submit), buyer.session(), future).andExpect(status().isConflict());
        mvc.perform(get("/api/appointments").session(stranger.session())).andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/appointments").session(buyer.session())).andExpect(jsonPath("$.items[0].note").value("想检查底盘"));
        mvc.perform(get("/api/admin/appointments").session(buyer.session())).andExpect(status().isForbidden());
        String cancel = "/api/appointments/" + booking + "/cancel";
        String handle = "/api/admin/appointments/" + booking + "/handle";
        call(post(cancel), stranger.session(), null).andExpect(status().isNotFound());
        call(post(handle), admin, "{\"status\":\"COMPLETED\"}").andExpect(status().isConflict());
        call(post(handle), admin, "{\"status\":\"CONFIRMED\",\"note\":\"请在展厅签到\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"));
        call(post(handle), admin, "{\"status\":\"COMPLETED\"}").andExpect(status().isConflict());
        call(post(cancel), buyer.session(), null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        call(post(cancel), buyer.session(), null).andExpect(status().isConflict());
        long next = read(call(post(submit), buyer.session(), future).andExpect(status().isCreated())).get("id").asLong();
        call(post("/api/admin/appointments/" + next + "/handle"), admin, "{\"status\":\"CONFIRMED\"}").andExpect(status().isOk());
        jdbc.update("UPDATE appointment SET visit_at = ? WHERE id = ?", now.minusMinutes(5), next);
        call(post("/api/admin/appointments/" + next + "/handle"), admin, "{\"status\":\"COMPLETED\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));
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
