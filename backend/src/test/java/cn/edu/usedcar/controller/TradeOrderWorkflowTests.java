package cn.edu.usedcar.controller; // 从 HTTP 边界验证订单事务。

import java.time.LocalDateTime; // 构造委托时间和到期边界。
import java.time.ZoneId; // 统一测试数据与系统的业务时间。
import java.util.ArrayList; // 保存并发请求及各自的返回结果。
import java.util.List; // 描述一组参与竞争的请求。
import java.util.UUID; // 为账号、字典和请求生成独立数据。
import java.util.concurrent.CountDownLatch; // 让并发请求在同一个时刻出发。
import java.util.concurrent.Executors; // 使用固定线程数运行并发验证。
import java.util.concurrent.TimeUnit; // 为测试线程和接口请求设置等待时限。
import com.fasterxml.jackson.databind.JsonNode; // 读取 HTTP JSON 返回。
import com.fasterxml.jackson.databind.ObjectMapper; // 序列化测试请求。
import org.junit.jupiter.api.Test; // 声明可单独执行的测试场景。
import org.springframework.beans.factory.annotation.Autowired; // 注入应用使用的测试依赖。
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc; // 激活真实的安全过滤器链。
import org.springframework.http.MediaType; // 指定 JSON 请求格式。
import org.springframework.jdbc.core.JdbcTemplate; // 直接核对交易、车源和锁记录。
import org.springframework.mock.web.MockHttpSession; // 为真实登录结果保存会话。
import org.springframework.test.web.servlet.MockMvc; // 通过 MVC 请求入口验证接口。
import org.springframework.test.web.servlet.MvcResult; // 收集并发请求的返回状态。
import org.springframework.test.web.servlet.ResultActions; // 验证单次请求的 JSON 和状态码。
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder; // 预先构造带会话的并发请求。

import static org.assertj.core.api.Assertions.assertThat; // 使用可读断言检查数据库结果。
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get; // 发起 GET 请求。
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post; // 发起 POST 请求。
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put; // 发起 PUT 请求。
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath; // 按路径读取返回字段。
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status; // 验证 HTTP 状态码。

/** 使用相同的业务验证覆盖 H2 与真实 MySQL。
 * @author pangzhihao
 */
@AutoConfigureMockMvc // 加载项目使用的登录、角色和 CSRF 保护。
abstract class TradeOrderWorkflowTests {
    private static final String PASSWORD = "local-order-test-password"; // 只用于测试环境的登录口令。
    @Autowired private MockMvc mvc; // 驱动真实控制器和安全过滤器。
    @Autowired private ObjectMapper json; // 序列化提交数据与解析响应。
    @Autowired private JdbcTemplate jdbc; // 直接核对接口变更后的持久化状态。

    @Test
    void directOrderUsesCurrentPriceAndOnlyParticipantsCanAct() throws Exception {
        var admin = login("order_admin"); // 审核车源和登记过户。
        var seller = user(); // 发布车辆并查看卖出订单。
        var buyer = user(); // 提交订单并完成模拟支付。
        var stranger = user(); // 验证无关账号的操作权限。
        var vehicle = listed(admin, seller, "DIRECT"); // 用正常业务接口创建直销车源。
        var other = listed(admin, seller, "DIRECT"); // 验证同一个请求标识不能用于另一辆车。
        postOrder(buyer, vehicle.id(), null).andExpect(status().isBadRequest()); // 下单必须携带有效请求标识。
        postOrder(seller, vehicle.id(), token()).andExpect(status().isBadRequest()); // 卖家无法购买本人车辆。
        String key = token(); // 用同一请求标识发起和重试。
        long orderId = id(postOrder(buyer, vehicle.id(), key).andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(52800.25)).andExpect(jsonPath("$.status").value("LOCKED"))
                .andExpect(jsonPath("$.consignmentId").doesNotExist())); // 服务读取车辆当前报价，直销订单没有委托引用。
        postOrder(buyer, vehicle.id(), key).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(orderId)); // 重试返回原订单。
        postOrder(buyer, other.id(), key).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED")); // 拒绝改变原请求车辆。
        mvc.perform(get("/api/vehicles/" + vehicle.id())).andExpect(status().isNotFound()); // 锁车后从市场隐藏。
        mvc.perform(get("/api/orders/" + orderId)).andExpect(status().isUnauthorized()); // 匿名不能读取订单。
        mvc.perform(get("/api/orders/" + orderId).session(stranger.session())).andExpect(status().isNotFound()); // 无关账号看不到订单。
        mvc.perform(get("/api/admin/orders/" + orderId).session(buyer.session())).andExpect(status().isForbidden()); // 普通用户无法访问后台详情。
        mvc.perform(get("/api/orders/" + orderId).session(seller.session())).andExpect(status().isOk()); // 卖家可访问作为交易一方的订单。
        mvc.perform(get("/api/orders").param("side", "BUY").session(buyer.session())).andExpect(jsonPath("$.total").value(1)); // 买入列表按买方查询。
        mvc.perform(get("/api/orders").param("side", "SELL").session(seller.session())).andExpect(jsonPath("$.total").value(1)); // 卖出列表按卖方查询。
        mvc.perform(get("/api/orders").session(stranger.session())).andExpect(jsonPath("$.total").value(0)); // 其他账号看到空的交易列表。
        submit(post("/api/admin/orders/" + orderId + "/complete"), admin, "{\"note\":\"过户凭证\"}").andExpect(status().isConflict()); // 未支付禁止成交。
        submit(post("/api/orders/" + orderId + "/pay"), seller.session(), null).andExpect(status().isForbidden()); // 只有买家能模拟支付。
        submit(post("/api/orders/" + orderId + "/cancel"), stranger.session(), null).andExpect(status().isNotFound()); // 无关账号不能取消。
        submit(post("/api/orders/" + orderId + "/pay"), buyer.session(), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID")).andExpect(jsonPath("$.paidAt").exists()); // 写入模拟支付状态和真实操作时间。
        submit(post("/api/orders/" + orderId + "/pay"), buyer.session(), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID")); // 网络重试保留原支付记录。
        submit(post("/api/orders/" + orderId + "/cancel"), buyer.session(), null).andExpect(status().isConflict()); // 已付款流程进入过户，不能用未付款取消入口。
        mvc.perform(get("/api/admin/orders").param("status", "PAID").session(admin))
                .andExpect(jsonPath("$.items[?(@.id == " + orderId + ")]").isNotEmpty()); // 后台待成交队列包含已付款订单。
        submit(post("/api/admin/orders/" + orderId + "/complete"), admin, "{\"note\":\"过户凭证\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED")).andExpect(jsonPath("$.completedAt").exists())
                .andExpect(jsonPath("$.handlingNote").value("过户凭证")); // 完成过户并保存说明。
        assertThat(vehicleState(vehicle.id())).isEqualTo("SOLD"); // 确保车源同步变成已售。
        assertThat(lockCount(vehicle.id())).isZero(); // 确保成交只释放对应的车辆锁。
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE subject_type = 'ORDER' AND subject_id = ?", Long.class, orderId))
                .isEqualTo(5L); // 下单、支付、成交产生约定的五条参与人通知，重复支付没有追加。
    }

    @Test
    void cancellationPreservesOtherLocksAndExpiredConsignmentGoesOffline() throws Exception {
        var admin = login("order_admin"); // 为代销车源审核授权。
        var seller = user(); // 提交委托并在付款前取消。
        var buyer = user(); // 为同一辆车建立两笔先后发生的订单。
        var vehicle = listed(admin, seller, "CONSIGN"); // 发布有有效委托的代销车。
        long first = id(postOrder(buyer, vehicle.id(), token()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.consignmentId").value(vehicle.consignmentId()))); // 保存原有效委托作为交易依据。
        submit(post("/api/orders/" + first + "/cancel"), seller.session(), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED")); // 卖家可以取消未付款订单。
        assertThat(vehicleState(vehicle.id())).isEqualTo("LISTED"); // 委托仍有效时恢复公开销售。
        long second = id(postOrder(buyer, vehicle.id(), token()).andExpect(status().isCreated())); // 车辆随后可被新订单占用。
        submit(post("/api/orders/" + first + "/cancel"), seller.session(), null).andExpect(status().isConflict()); // 旧订单不能释放新锁。
        assertThat(jdbc.queryForObject("SELECT order_id FROM vehicle_lock WHERE vehicle_id = ?", Long.class, vehicle.id())).isEqualTo(second); // 验证车辆仍锁在第二笔订单上。
        jdbc.update("UPDATE vehicle_lock SET order_id = ? WHERE vehicle_id = ?", first, vehicle.id()); // 模拟历史异常中锁记录指向了错误订单。
        submit(post("/api/orders/" + second + "/cancel"), buyer.session(), null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_LOCK_CONFLICT")); // 即便订单状态允许，锁归属不符也不得处理。
        assertThat(lockCount(vehicle.id())).isOne(); // 核对错误的锁未被删掉。
        jdbc.update("UPDATE vehicle_lock SET order_id = ? WHERE vehicle_id = ?", second, vehicle.id()); // 还原正确配对以继续到期分支。
        expire(vehicle); // 在已锁车后使委托过期。
        submit(post("/api/orders/" + second + "/cancel"), buyer.session(), null).andExpect(status().isOk()); // 代销约定到期时仍可取消未付款订单。
        assertThat(vehicleState(vehicle.id())).isEqualTo("OFFLINE"); // 到期车源恢复为已下架，而不是重新公开。
        mvc.perform(get("/api/vehicles/" + vehicle.id())).andExpect(status().isNotFound()); // 已无有效代销委托的车辆继续隐藏。
    }

    @Test
    void existingConsignmentOrderCanCompleteAfterExpiry() throws Exception {
        var admin = login("order_admin"); // 登记买家已付款后的过户结果。
        var seller = user(); // 提交有效委托。
        var buyer = user(); // 在委托期间买车。
        var expired = listed(admin, seller, "CONSIGN"); // 准备一辆待验证到期拦截的代销车。
        expire(expired); // 让这辆车的委托在下单前到期。
        postOrder(buyer, expired.id(), token()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONSIGNMENT_REQUIRED")); // 到期委托阻止新订单。
        assertThat(lockCount(expired.id())).isZero(); // 校验失败不能挂上车辆锁。
        var valid = listed(admin, seller, "CONSIGN"); // 准备一辆当前有有效委托的车辆。
        long order = id(postOrder(buyer, valid.id(), token()).andExpect(status().isCreated())); // 有效期内保存订单。
        expire(valid); // 已锁定的订单继续按原委托办理。
        submit(post("/api/orders/" + order + "/pay"), buyer.session(), null).andExpect(status().isOk()); // 到期不撤销先前交易。
        submit(post("/api/admin/orders/" + order + "/complete"), admin, "{}").andExpect(status().isOk()); // 完成已付款代销订单。
        assertThat(jdbc.queryForObject("SELECT status FROM consignment WHERE id = ?", String.class, valid.consignmentId()))
                .isEqualTo("COMPLETED"); // 交易成交后同时结案原始委托。
        assertThat(vehicleState(valid.id())).isEqualTo("SOLD"); // 同时核对车辆状态。
    }

    @Test
    void concurrentRequestsProduceOnlyOneOrderAndOneLock() throws Exception {
        var admin = login("order_admin"); // 使用真实的上架流程准备同一辆车。
        var seller = user(); // 为车源提供独立卖家身份。
        var vehicle = listed(admin, seller, "DIRECT"); // 所有并发请求争用这一行车辆。
        var requests = new ArrayList<MockHttpServletRequestBuilder>(); // 提前准备会话，避免把登录放进并发测试。
        for (int i = 0; i < 20; i++) {
            requests.add(orderRequest(user(), vehicle.id(), token())); // 为每个并发请求提供独立买家和请求标识。
        }
        var responses = race(requests); // 同时发出二十次下单请求。
        assertThat(responses.stream().map(result -> result.getResponse().getStatus()).filter(code -> code == 201).count()).isOne(); // 只有一位买家取得订单。
        assertThat(responses.stream().map(result -> result.getResponse().getStatus()).filter(code -> code == 409).count()).isEqualTo(19L); // 其余请求得到确定的业务冲突。
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM trade_order WHERE vehicle_id = ?", Long.class, vehicle.id())).isOne(); // 确认没有竞争失败遗留的订单。
        assertThat(lockCount(vehicle.id())).isOne(); // 数据库中同车恰有一条锁记录。
    }

    @Test
    void concurrentSameKeyAcrossVehiclesRollsBackLosingReservation() throws Exception {
        var admin = login("order_admin"); // 准备同一买家可见的两辆直销车。
        var seller = user(); // 确保买家和卖家身份不同。
        var buyer = user(); // 使用同一买家的同一个请求标识模拟并发重试。
        var left = listed(admin, seller, "DIRECT"); // 第一辆车独立加锁。
        var right = listed(admin, seller, "DIRECT"); // 第二辆车独立加锁。
        var key = token(); // 让数据库唯一约束拦截跨车的并发复用。
        var responses = race(List.of(orderRequest(buyer, left.id(), key), orderRequest(buyer, right.id(), key))); // 让两个事务同时尝试占用。
        assertThat(responses.stream().map(result -> result.getResponse().getStatus())).containsExactlyInAnyOrder(201, 409); // 胜者成功，另一笔返回请求标识冲突。
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM trade_order WHERE buyer_id = ?", Long.class, buyer.id())).isOne(); // 只有一条幂等订单保存。
        assertThat(lockCount(left.id()) + lockCount(right.id())).isOne(); // 回滚失败请求对另一辆车的锁定。
        assertThat(List.of(vehicleState(left.id()), vehicleState(right.id()))).containsExactlyInAnyOrder("LISTED", "LOCKED"); // 保持成功和失败车辆的状态一致。
    }

    private List<MvcResult> race(List<MockHttpServletRequestBuilder> requests) throws Exception {
        var executor = Executors.newFixedThreadPool(requests.size()); // 为每个请求启动一个独立执行线程。
        var ready = new CountDownLatch(requests.size()); // 直到全部请求准备好才继续。
        var start = new CountDownLatch(1); // 所有请求共享同一个起跑信号。
        try {
            var running = requests.stream().map(request -> executor.submit(() -> {
                ready.countDown(); // 标记当前线程已准备就绪。
                if (!start.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("并发起跑等待超时"); // 避免失败后无限等待。
                return mvc.perform(request).andReturn(); // 通过应用 HTTP 入口执行。
            })).toList();
            assertThat(ready.await(20, TimeUnit.SECONDS)).isTrue(); // 确认所有线程就位。
            start.countDown(); // 同时释放所有请求。
            var responses = new ArrayList<MvcResult>(); // 顺序收集结果用于验收。
            for (var task : running) responses.add(task.get(40, TimeUnit.SECONDS)); // 规定每个任务的最大等待时间。
            return responses; // 将所有结果交给各个测试场景。
        } finally {
            executor.shutdownNow(); // 测试结束后清理并发线程。
        }
    }

    private TradeVehicle listed(MockHttpSession admin, TestUser seller, String saleMode) throws Exception {
        long brand = dictionary(admin, "BRAND"); // 单独建立有效品牌。
        long city = dictionary(admin, "CITY"); // 单独建立有效城市。
        var data = json.createObjectNode().put("brandId", brand).put("cityId", city).put("saleMode", saleMode)
                .put("model", "验收车辆").put("registrationYear", 2020).put("price", new java.math.BigDecimal("52800.25"))
                .put("mileage", 32000).put("transmission", "AUTO").put("fuel", "PETROL").put("description", "订单验收用车"); // 使用正常车源表单。
        var car = read(submit(post("/api/vehicles"), seller.session(), data.toString()).andExpect(status().isCreated())); // 车主提交到待审。
        long vehicleId = car.get("id").asLong(); // 保存新车源编号。
        Long consignmentId = null; // 直销车辆没有委托。
        if ("CONSIGN".equals(saleMode)) {
            var now = LocalDateTime.now(ZoneId.of("Asia/Shanghai")); // 用业务时区设置当前有效的代销期限。
            var agreement = json.createObjectNode().put("startsAt", now.minusHours(2).toString())
                    .put("expiresAt", now.plusDays(2).toString()).put("fee", 700); // 保存可用于正常下单的委托表单。
            consignmentId = id(submit(post("/api/vehicles/" + vehicleId + "/consignments"), seller.session(), agreement.toString())
                    .andExpect(status().isCreated())); // 让委托走真实提交。
            submit(post("/api/admin/consignments/" + consignmentId + "/review"), admin, "{\"decision\":\"APPROVE\"}")
                    .andExpect(status().isOk()); // 工作人员通过委托。
        }
        var review = json.createObjectNode().put("revision", car.get("revision").asLong()).put("decision", "APPROVE"); // 携带本次车源审核版本。
        submit(post("/api/admin/vehicles/" + vehicleId + "/review"), admin, review.toString()).andExpect(status().isOk()); // 正式放入市场。
        return new TradeVehicle(vehicleId, consignmentId); // 为断言保留车源及可选委托编号。
    }

    private void expire(TradeVehicle vehicle) {
        var now = LocalDateTime.now(ZoneId.of("Asia/Shanghai")); // 取同一业务时区下的当前时刻。
        jdbc.update("UPDATE consignment SET expires_at = ? WHERE id = ?", now.minusHours(1), vehicle.consignmentId()); // 将测试委托提前到期，无需睡眠。
    }

    private long lockCount(long id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM vehicle_lock WHERE vehicle_id = ?", Long.class, id); // 查询车辆锁历史外的当前锁记录。
    }

    private String vehicleState(long id) {
        return jdbc.queryForObject("SELECT status FROM vehicle WHERE id = ?", String.class, id); // 从库中取得实际保存的车辆状态。
    }

    private ResultActions postOrder(TestUser user, long vehicleId, String key) throws Exception {
        return mvc.perform(orderRequest(user, vehicleId, key)); // 发起构造好的下单请求。
    }

    private MockHttpServletRequestBuilder orderRequest(TestUser user, long vehicleId, String key) throws Exception {
        var request = withCsrf(post("/api/orders"), user.session(), json.createObjectNode().put("vehicleId", vehicleId).toString()); // 构造真实下单 JSON。
        if (key != null) request.header("Idempotency-Key", key); // 支持验证缺少请求标识的情况。
        return request; // 返回可同步或并发执行的请求。
    }

    private long dictionary(MockHttpSession admin, String type) throws Exception {
        var body = json.createObjectNode().put("name", type + "-" + token().substring(0, 8)).put("active", true); // 用唯一名称创建活跃字典。
        return id(submit(post("/api/admin/dictionaries/" + type), admin, body.toString()).andExpect(status().isCreated())); // 获得该字典项的编号。
    }

    private TestUser user() throws Exception {
        String username = "order_" + token().substring(0, 12); // 为每个参与人生成独立账号。
        var data = json.createObjectNode().put("username", username).put("password", PASSWORD).put("displayName", username); // 构造普通账号注册数据。
        submit(post("/api/auth/register"), null, data.toString()).andExpect(status().isCreated()); // 经真实认证接口登记。
        return new TestUser(login(username)); // 生成带有效认证的会话。
    }

    private MockHttpSession login(String username) throws Exception {
        var data = json.createObjectNode().put("username", username).put("password", PASSWORD); // 发送账号与测试口令。
        return (MockHttpSession) submit(post("/api/auth/login"), null, data.toString()).andExpect(status().isOk())
                .andReturn().getRequest().getSession(false); // 保存后续请求使用的会话。
    }

    private ResultActions submit(MockHttpServletRequestBuilder request, MockHttpSession session, String body) throws Exception {
        return mvc.perform(withCsrf(request, session, body)); // 统一为写请求补充真实 CSRF 令牌。
    }

    private MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder request, MockHttpSession session, String body) throws Exception {
        var csrf = get("/api/auth/csrf"); // 通过公共认证接口获取写请求令牌。
        if (session != null) csrf.session(session); // 已登录用户沿用会话。
        var response = mvc.perform(csrf).andExpect(status().isOk()).andReturn(); // 取得当前会话令牌。
        var data = json.readTree(response.getResponse().getContentAsByteArray()); // 解析令牌名称和值。
        request.session((MockHttpSession) response.getRequest().getSession(false))
                .header(data.get("headerName").asText(), data.get("token").asText()); // 将令牌与会话一起交给业务请求。
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(body); // 有表单时附上 JSON 请求体。
        return request; // 返回可执行的请求。
    }

    private JsonNode read(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsByteArray()); // 将接口响应转换为可读树。
    }

    private long id(ResultActions result) throws Exception {
        return read(result).get("id").asLong(); // 读取创建接口返回的业务编号。
    }

    private static String token() {
        return UUID.randomUUID().toString(); // 为不同请求和测试数据生成唯一标识。
    }

    private record TestUser(MockHttpSession session) { } // 保存买卖双方的登录会话。
    private record TradeVehicle(long id, Long consignmentId) { } // 保存测试车源和可选代销委托编号。
}
