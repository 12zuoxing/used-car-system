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
    void loanIsPrivateAndValidatesOrderAmountAndTerm() throws Exception {
        var admin = login("order_admin"); // 用正常业务接口审核测试车源。
        var seller = user(); // 卖家能够看订单但无权看买家的贷款资料。
        var buyer = user(); // 只有下单买家才能登记贷款。
        var other = user(); // 第三方编号用于验证越权响应。
        var car = listed(admin, seller, "DIRECT"); // 准备独立车源避免测试数据互相影响。
        long order = id(postOrder(buyer, car.id(), token()).andExpect(status().isCreated())); // 保存实际锁车订单作为登记对象。
        String path = "/api/orders/" + order + "/loan"; // 确保贷款与路径指向的订单绑定。
        String input = "{\"amount\":52800.25,\"months\":84,\"note\":\"买家补充说明\"}"; // 使用恰好等于订单金额和期限上限的表单。
        mvc.perform(get(path).session(buyer.session())).andExpect(status().isNoContent()); // 买家可确认订单还没有贷款申请。
        submit(post(path), seller.session(), input).andExpect(status().isNotFound()); // 卖家不能替买家提交。
        submit(post(path), other.session(), input).andExpect(status().isNotFound()); // 其他人不知道订单是否存在。
        submit(post(path), buyer.session(), "{\"amount\":52800.26,\"months\":12}").andExpect(status().isBadRequest()); // 超过订单金额一分钱也要拒绝。
        submit(post(path), buyer.session(), "{\"amount\":20000,\"months\":85}").andExpect(status().isBadRequest()); // 拒绝超过 84 期。
        submit(post(path), buyer.session(), "{\"amount\":20000,\"months\":0}").andExpect(status().isBadRequest()); // 拒绝低于一期。
        long loan = id(submit(post(path), buyer.session(), input).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))); // 合法表单创建一条待处理的登记。
        submit(post(path), buyer.session(), input).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LOAN_ALREADY_EXISTS")); // 同订单一份申请。
        mvc.perform(get(path).session(buyer.session())).andExpect(jsonPath("$.id").value(loan)); // 订单可查到本人的原登记。
        mvc.perform(get(path).session(seller.session())).andExpect(status().isNotFound()); // 卖家同样无法用订单路径查询。
        mvc.perform(get("/api/loans/" + loan).session(other.session())).andExpect(status().isNotFound()); // 直接改贷款编号同样失败。
        mvc.perform(get("/api/admin/loans/" + loan).session(buyer.session())).andExpect(status().isForbidden()); // 买家无权访问管理入口。
        mvc.perform(get("/api/loans").session(buyer.session())).andExpect(jsonPath("$.total").value(1)); // 买家列表只展示自己的申请。
        mvc.perform(get("/api/loans").session(seller.session())).andExpect(jsonPath("$.total").value(0)); // 卖家不能通过分页获取申请。
        assertThat(jdbc.queryForObject("SELECT status FROM trade_order WHERE id = ?", String.class, order))
                .isEqualTo("LOCKED"); // 贷款登记本身不改变订单的支付状态。
    }

    @Test
    void loanSupplementReusesRecordAndRejectsStaleHandling() throws Exception {
        var admin = login("order_admin"); // 通过管理员授权普通工作人员角色。
        var staff = user(); // 使用工作人员会话办理贷款资料。
        submit(put("/api/admin/users/" + staff.id() + "/roles"), admin, "{\"roles\":[\"STAFF\"]}").andExpect(status().isOk()); // 让后台角色与申请人分开。
        var seller = user(); // 提供申请对应的车辆和交易卖家。
        var buyer = user(); // 用交易买家的身份提交和修改资料。
        var car = listed(admin, seller, "DIRECT"); // 沿用正常业务流程上架可交易车源。
        long order = id(postOrder(buyer, car.id(), token()).andExpect(status().isCreated())); // 生成用于申请贷款的订单记录。
        String initial = "{\"amount\":23000,\"months\":12,\"note\":\"初次登记\"}"; // 初次登记包含金额、期限和用途说明。
        var created = read(submit(post("/api/orders/" + order + "/loan"), buyer.session(), initial).andExpect(status().isCreated())); // 保留初次审核版本。
        long loan = created.get("id").asLong(); // 所有补充和后台处理都复用这个编号。
        String detail = "/api/loans/" + loan; // 申请人读取及更新申请的路径。
        String handle = "/api/admin/loans/" + loan + "/status"; // 工作人员改变办理状态的路径。
        mvc.perform(get("/api/admin/loans/" + loan).session(staff.session())).andExpect(jsonPath("$.note").value("初次登记")); // 授权工作人员能查申请详情。
        submit(put(detail), buyer.session(), initial).andExpect(status().isConflict()); // 仍在待处理时申请人不能直接覆盖审核资料。
        submit(post(handle), staff.session(), loanStatus("FINISHED", "提前结束", created)).andExpect(status().isConflict()); // 待处理不可跳过办理直接完成。
        submit(post(handle), staff.session(), loanStatus("SUPPLEMENT", "  ", created)).andExpect(status().isBadRequest()); // 退回要有实际的补充要求。
        var supplement = read(submit(post(handle), staff.session(), loanStatus("SUPPLEMENT", "请补充还款安排", created))
                .andExpect(status().isOk()).andExpect(jsonPath("$.resultNote").value("请补充还款安排"))); // 审核员退回并留下一条说明。
        var replacement = "{\"amount\":22000,\"months\":1,\"note\":\"已补充说明\"}"; // 在允许范围内更新金额和期限。
        submit(put(detail), seller.session(), replacement).andExpect(status().isNotFound()); // 只有申请人能补充资料。
        var resubmitted = read(submit(put(detail).param("revision", supplement.get("revision").asText()), buyer.session(), replacement)
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(loan))
                .andExpect(jsonPath("$.status").value("PENDING")).andExpect(jsonPath("$.months").value(1))); // 更新原记录并回到待处理。
        submit(post(handle), staff.session(), loanStatus("PROCESSING", "", created)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_LOAN")); // 上一次的审核页面无法处理补充后的数据。
        var processing = read(submit(post(handle), staff.session(), loanStatus("PROCESSING", "", resubmitted))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PROCESSING"))); // 工作人员可以开始办理。
        submit(post(handle), staff.session(), loanStatus("FINISHED", "人工登记已完成", processing))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FINISHED")); // 办理完成时留下一条结果说明。
        mvc.perform(get("/api/admin/loans").param("status", "FINISHED").session(staff.session()))
                .andExpect(jsonPath("$.items[?(@.id == " + loan + ")]").isNotEmpty()); // 通过后台筛选可查完成的登记。
        mvc.perform(get(detail).session(buyer.session())).andExpect(jsonPath("$.resultNote").value("人工登记已完成")); // 申请人能看到最新的办理结论。
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM loan_application WHERE order_id = ?", Long.class, order)).isOne(); // 补充资料未创建第二条记录。
        submit(post("/api/orders/" + order + "/cancel"), buyer.session(), null).andExpect(status().isOk()); // 办理结果和模拟支付保持独立。
        mvc.perform(get(detail).session(buyer.session())).andExpect(jsonPath("$.status").value("FINISHED")); // 已完成的登记保留历史结果。
    }

    @Test
    void duplicateLoanRequestsAndOrderCancellationKeepStateConsistent() throws Exception {
        var admin = login("order_admin"); // 建立真实的审核上下文。
        var seller = user(); // 车主用于测试取消所占用的车辆。
        var buyer = user(); // 两个并发贷款请求共享同一买家和订单。
        var car = listed(admin, seller, "DIRECT"); // 准备在售车辆。
        long order = id(postOrder(buyer, car.id(), token()).andExpect(status().isCreated())); // 建立待支付订单。
        String path = "/api/orders/" + order + "/loan"; // 同一订单的贷款入口。
        String form = "{\"amount\":26000,\"months\":36}"; // 不带可选备注的合法申请。
        var results = race(List.of(withCsrf(post(path), buyer.session(), form), withCsrf(post(path), buyer.session(), form))); // 同时提交两次同订单申请。
        assertThat(results.stream().map(result -> result.getResponse().getStatus())).containsExactlyInAnyOrder(201, 409); // 并发也只保留一次有效登记。
        long loan = read(mvc.perform(get(path).session(buyer.session())).andExpect(status().isOk())).get("id").asLong(); // 读取成功保留的申请编号。
        submit(post("/api/orders/" + order + "/cancel"), seller.session(), null).andExpect(status().isOk()); // 订单取消应在同一事务处理未结贷款。
        mvc.perform(get("/api/loans/" + loan).session(buyer.session())).andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.resultNote").value("关联订单已取消。")); // 保留买家可见的关闭原因。
        submit(post(path), buyer.session(), form).andExpect(status().isConflict()); // 已取消订单无法重新登记。
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE subject_type = 'LOAN' AND subject_id = ?", Long.class, loan))
                .isEqualTo(1L); // 订单取消只登记一次贷款关闭通知。
    }

    private String loanStatus(String state, String note, JsonNode revision) {
        return json.createObjectNode().put("status", state).put("resultNote", note)
                .put("revision", revision.get("revision").asLong()).toString(); // 携带审核员读取到的贷款版本。
    }

    @Test
    void preSaleRepairChecksOwnershipAndGoesThroughProcessing() throws Exception {
        var admin = login("order_admin"); // 准备真实车源审核和工作人员授权。
        var staff = user(); // 使用独立工作人员办理工单。
        submit(put("/api/admin/users/" + staff.id() + "/roles"), admin, "{\"roles\":[\"STAFF\"]}").andExpect(status().isOk()); // 按后台实际角色规则授权。
        var seller = user(); // 车源发布人可登记售前整备。
        var outsider = user(); // 无关账号用于验收资源隔离。
        var car = listed(admin, seller, "DIRECT"); // 走正常发布和上架流程。
        var request = repair("PRE_SALE", car.id(), null); // 售前只需要绑定本人车辆。
        submit(post("/api/repairs"), outsider.session(), request.toString()).andExpect(status().isNotFound()); // 无关用户不能引用卖家车辆。
        var past = request.deepCopy().put("visitAt", LocalDateTime.now(ZoneId.of("Asia/Shanghai")).minusHours(1).toString()); // 模拟已经过去的到店时间。
        submit(post("/api/repairs"), seller.session(), past.toString()).andExpect(status().isBadRequest()); // 已过去的时间无法提交。
        var blank = request.deepCopy().put("description", "  "); // 模拟没有写整备要求的申请。
        submit(post("/api/repairs"), seller.session(), blank.toString()).andExpect(status().isBadRequest()); // 空白说明无效。
        long id = id(submit(post("/api/repairs"), seller.session(), request.toString()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING")).andExpect(jsonPath("$.tradeOrderId").doesNotExist())); // 售前不保存订单外键。
        String detail = "/api/repairs/" + id; // 保存可从个人中心打开的详情路径。
        String adminStatus = "/api/admin/repairs/" + id + "/status"; // 后台经办地址。
        mvc.perform(get(detail).session(outsider.session())).andExpect(status().isNotFound()); // 工单描述只向授权账号开放。
        mvc.perform(get("/api/repairs").session(outsider.session())).andExpect(jsonPath("$.total").value(0)); // 无关用户列表不可见。
        mvc.perform(get("/api/admin/repairs").session(seller.session())).andExpect(status().isForbidden()); // 普通车主不能查看后台队列。
        mvc.perform(get("/api/admin/repairs/" + id).session(staff.session())).andExpect(jsonPath("$.description").value("请检查底盘和轮胎")); // 经办人可核对详情。
        submit(post(adminStatus), staff.session(), repairStatus("COMPLETED", "确认完工")).andExpect(status().isConflict()); // 工单必须先受理才能完工。
        submit(post(adminStatus), staff.session(), repairStatus("PROCESSING", "已安排检查"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PROCESSING")); // 工作人员开始处理。
        submit(post(detail + "/cancel"), seller.session(), null).andExpect(status().isConflict()); // 已受理工单不允许申请人直接取消。
        submit(post(adminStatus), staff.session(), repairStatus("COMPLETED", " ")).andExpect(status().isBadRequest()); // 完工需要业务说明。
        submit(post(adminStatus), staff.session(), repairStatus("COMPLETED", "已复核底盘及轮胎"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.completedAt").exists()); // 写入完工时间和说明。
        submit(post(adminStatus), staff.session(), repairStatus("CANCELLED", "完成后取消")).andExpect(status().isConflict()); // 工单完成后拒绝改状态。
        mvc.perform(get("/api/repairs").param("stage", "PRE_SALE").param("status", "COMPLETED").session(seller.session()))
                .andExpect(jsonPath("$.items[0].id").value(id)); // 申请人可在自己的售前工单过滤中查到历史。
        mvc.perform(get(detail).session(seller.session())).andExpect(jsonPath("$.resultNote").value("已复核底盘及轮胎")); // 申请人能读办理说明。
        assertThat(vehicleState(car.id())).isEqualTo("LISTED"); // 售前办理独立于车辆上架状态。
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE subject_type = 'REPAIR' AND subject_id = ?", Long.class, id))
                .isEqualTo(2L); // 受理与完工各留下通知。
    }

    @Test
    void afterSaleRepairRequiresMatchingCompletedOrderAndCanBeCancelledOrCompleted() throws Exception {
        var admin = login("order_admin"); // 通过实际审核和交易接口完成测试订单。
        var seller = user(); // 即使卖家能看订单，也不能替买家提交售后。
        var buyer = user(); // 成交订单买方可登记售后。
        var car = listed(admin, seller, "DIRECT"); // 保存订单对应的实际车辆。
        var otherCar = listed(admin, seller, "DIRECT"); // 另一辆车辆用于验证关联校验。
        long order = id(postOrder(buyer, car.id(), token()).andExpect(status().isCreated())); // 创建未支付订单。
        String form = repair("AFTER_SALE", car.id(), order).toString(); // 售后绑定成交车辆和订单。
        submit(post("/api/repairs"), buyer.session(), repair("AFTER_SALE", car.id(), null).toString())
                .andExpect(status().isBadRequest()); // 售后必须提供订单依据。
        submit(post("/api/repairs"), seller.session(), repair("PRE_SALE", car.id(), order).toString())
                .andExpect(status().isBadRequest()); // 售前不能混用订单外键。
        submit(post("/api/repairs"), buyer.session(), form).andExpect(status().isConflict()); // 订单未成交时不能申请售后。
        submit(post("/api/orders/" + order + "/pay"), buyer.session(), null).andExpect(status().isOk()); // 完成模拟支付。
        submit(post("/api/admin/orders/" + order + "/complete"), admin, "{}").andExpect(status().isOk()); // 登记确认过户。
        submit(post("/api/repairs"), seller.session(), form).andExpect(status().isNotFound()); // 只有原订单买家可访问售后入口。
        submit(post("/api/repairs"), buyer.session(), repair("AFTER_SALE", otherCar.id(), order).toString())
                .andExpect(status().isBadRequest()); // 同一笔订单不能给另一辆车申请送修。
        submit(post("/api/repairs"), seller.session(), repair("PRE_SALE", car.id(), null).toString())
                .andExpect(status().isConflict()); // 已交付车辆不能再按卖家的售前整备提交。
        long first = id(submit(post("/api/repairs"), buyer.session(), form).andExpect(status().isCreated())
                .andExpect(jsonPath("$.tradeOrderId").value(order)).andExpect(jsonPath("$.stage").value("AFTER_SALE"))); // 保存通过校验的售后关系。
        submit(post("/api/repairs/" + first + "/cancel"), seller.session(), null).andExpect(status().isNotFound()); // 他人不能取消买家的工单。
        submit(post("/api/repairs/" + first + "/cancel"), buyer.session(), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED")).andExpect(jsonPath("$.completedAt").doesNotExist()); // 申请人可取消尚未受理的送修。
        long second = id(submit(post("/api/repairs"), buyer.session(), form).andExpect(status().isCreated())); // 保留历史的同时允许另起一笔送修。
        String processing = "/api/admin/repairs/" + second + "/status"; // 从后台处理真实售后工单。
        submit(post(processing), admin, repairStatus("PROCESSING", "车辆已接收")).andExpect(status().isOk()); // 工作人员接单。
        submit(post(processing), admin, repairStatus("COMPLETED", "更换磨损部件并复检")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED")); // 保存售后完工结果。
        assertThat(vehicleState(car.id())).isEqualTo("SOLD"); // 售后流程不会更改原成交状态。
    }

    @Test
    void repairCancellationAndStaffIntakeAreSerialized() throws Exception {
        var admin = login("order_admin"); // 使用后台审核会话接单。
        var seller = user(); // 同一笔整备工单的申请方。
        var car = listed(admin, seller, "DIRECT"); // 准备本人可预约整备的车辆。
        long id = id(submit(post("/api/repairs"), seller.session(), repair("PRE_SALE", car.id(), null).toString())
                .andExpect(status().isCreated())); // 创建一笔待处理工单。
        var results = race(List.of(withCsrf(post("/api/repairs/" + id + "/cancel"), seller.session(), null),
                withCsrf(post("/api/admin/repairs/" + id + "/status"), admin, repairStatus("PROCESSING", "经办人已接单")))); // 同时取消和受理。
        assertThat(results.stream().map(result -> result.getResponse().getStatus())).containsExactlyInAnyOrder(200, 409); // 两个状态变更只能成功一个。
        var record = read(mvc.perform(get("/api/repairs/" + id).session(seller.session())).andExpect(status().isOk())); // 查询实际提交的状态。
        assertThat(record.get("status").asText()).isIn("CANCELLED", "PROCESSING"); // 失败事务不能覆盖赢家结果。
        if (record.get("status").asText().equals("PROCESSING")) {
            submit(post("/api/admin/repairs/" + id + "/status"), admin, repairStatus("CANCELLED", "无法安排送修"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.resultNote").value("无法安排送修")); // 工作人员可为处理中的工单登记取消原因。
        }
    }

    private com.fasterxml.jackson.databind.node.ObjectNode repair(String stage, long vehicleId, Long orderId) {
        var input = json.createObjectNode().put("stage", stage).put("vehicleId", vehicleId)
                .put("visitAt", LocalDateTime.now(ZoneId.of("Asia/Shanghai")).plusDays(1).toString())
                .put("description", "请检查底盘和轮胎"); // 构造带未来到店时刻的工单表单。
        if (orderId != null) input.put("tradeOrderId", orderId); // 售后工单填写准确的订单号。
        return input; // 交给调用的测试按具体场景修改。
    }

    private String repairStatus(String status, String note) {
        return json.createObjectNode().put("status", status).put("resultNote", note).toString(); // 后台提交状态和给申请人看的处理说明。
    }

    @Test
    void notificationInboxUsesRecipientAndReadIsIdempotent() throws Exception {
        var admin = login("order_admin"); // 用审核角色建立实际交易。
        var seller = user(); // 卖家接收自己的订单通知。
        var buyer = user(); // 买家收到模拟付款成功通知。
        var car = listed(admin, seller, "DIRECT"); // 通过正常业务创建在售车源。
        long order = id(postOrder(buyer, car.id(), token()).andExpect(status().isCreated())); // 锁车时先通知卖家。
        mvc.perform(get("/api/notifications").session(buyer.session())).andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(0)); // 买家与卖家的通知分开读取。
        submit(post("/api/orders/" + order + "/pay"), buyer.session(), null).andExpect(status().isOk()); // 触发真实业务通知。
        var inbox = read(mvc.perform(get("/api/notifications").param("unreadOnly", "true").session(buyer.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.unreadCount").value(1))
                .andExpect(jsonPath("$.items[0].subjectId").value(order))); // 即使没有连接在线，也能通过列表补读。
        long notice = inbox.get("items").get(0).get("id").asLong(); // 读取这条通知的业务编号。
        mvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized()); // 匿名不能读取通知。
        mvc.perform(get("/ws/notifications")).andExpect(status().isUnauthorized()); // WebSocket 握手要求已登录会话。
        submit(post("/api/notifications/" + notice + "/read"), seller.session(), null).andExpect(status().isNotFound()); // 他人不能代标已读。
        submit(post("/api/notifications/" + notice + "/read"), buyer.session(), null).andExpect(status().isNoContent()); // 本人可标记已读。
        var time = jdbc.queryForObject("SELECT read_at FROM notification WHERE id = ?", LocalDateTime.class, notice); // 保存第一次标记的数据库时间。
        submit(post("/api/notifications/" + notice + "/read"), buyer.session(), null).andExpect(status().isNoContent()); // 重复请求返回同样的成功结果。
        assertThat(jdbc.queryForObject("SELECT read_at FROM notification WHERE id = ?", LocalDateTime.class, notice)).isEqualTo(time); // 不覆盖首次已读时间。
        mvc.perform(get("/api/notifications").param("unreadOnly", "true").session(buyer.session()))
                .andExpect(jsonPath("$.total").value(0)).andExpect(jsonPath("$.unreadCount").value(0)); // 已读后从未读列表移除。
        mvc.perform(get("/api/notifications").session(buyer.session())).andExpect(jsonPath("$.items[0].readAt").exists())
                .andExpect(jsonPath("$.items[0].eventKey").doesNotExist()); // 历史保留，内部去重标识不进入客户端。
    }

    @Test
    void dashboardCountsMatchQueuesAndAuditHidesRestrictedDetails() throws Exception {
        var admin = login("order_admin"); // 管理员可查看账号安全审计和完整说明。
        var staff = user(); // 工作人员可查看工作台及业务审计摘要。
        var member = user(); // 普通账号用于验证后台访问限制。
        submit(put("/api/admin/users/" + staff.id() + "/roles"), admin, "{\"roles\":[\"STAFF\"]}").andExpect(status().isOk()); // 通过实际接口完成角色授权。
        var vehicle = listed(admin, member, "DIRECT"); // 生成一组真实车源审核日志。
        postOrder(staff, vehicle.id(), token()).andExpect(status().isCreated()); // 生成一个实际在办订单。
        jdbc.update("INSERT INTO audit_log (actor_id, action, subject_type, subject_id, detail) VALUES (?, ?, ?, ?, ?)",
                member.id(), "SUBMIT_VEHICLE", "VEHICLE", vehicle.id(), "用于核对权限的私密审计说明"); // 模拟历史自由文本记录。
        var dashboard = read(mvc.perform(get("/api/admin/dashboard").session(staff.session())).andExpect(status().isOk())); // 读取当前工作台快照。
        assertThat(dashboard.get("pendingVehicles").asLong()).isEqualTo(queueTotal("/api/admin/vehicles", "PENDING", admin)); // 待审车辆与队列一致。
        assertThat(dashboard.get("lockedOrders").asLong()).isEqualTo(queueTotal("/api/admin/orders", "LOCKED", admin)); // 待付款订单与队列一致。
        assertThat(dashboard.get("paidOrders").asLong()).isEqualTo(queueTotal("/api/admin/orders", "PAID", admin)); // 待过户订单与队列一致。
        assertThat(dashboard.get("pendingLoans").asLong()).isEqualTo(queueTotal("/api/admin/loans", "PENDING", admin)); // 待处理贷款与队列一致。
        assertThat(dashboard.get("processingLoans").asLong()).isEqualTo(queueTotal("/api/admin/loans", "PROCESSING", admin)); // 办理中贷款与队列一致。
        assertThat(dashboard.get("pendingRepairs").asLong()).isEqualTo(queueTotal("/api/admin/repairs", "PENDING", admin)); // 待受理工单与队列一致。
        assertThat(dashboard.get("processingRepairs").asLong()).isEqualTo(queueTotal("/api/admin/repairs", "PROCESSING", admin)); // 处理中的工单与队列一致。
        mvc.perform(get("/api/admin/dashboard").session(member.session())).andExpect(status().isForbidden()); // 普通账号无法访问统计。
        String audit = "/api/admin/audit-logs"; // 审计分页的访问路径。
        mvc.perform(get(audit).session(member.session())).andExpect(status().isForbidden()); // 普通账号无法访问操作日志。
        mvc.perform(get(audit).session(staff.session()).param("subjectType", "USER"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0)); // 工作人员不能通过筛选读取账号安全事件。
        mvc.perform(get(audit).session(staff.session()).param("subjectType", "VEHICLE").param("subjectId", Long.toString(vehicle.id())))
                .andExpect(jsonPath("$.items[0].action").value("SUBMIT_VEHICLE"))
                .andExpect(jsonPath("$.items[0].detail").doesNotExist()); // 工作人员只得到业务事件的结构化信息。
        mvc.perform(get(audit).session(admin).param("subjectType", "VEHICLE").param("subjectId", Long.toString(vehicle.id())))
                .andExpect(jsonPath("$.items[0].detail").value("用于核对权限的私密审计说明")); // 管理员可核查原审计说明。
        mvc.perform(get(audit).session(admin).param("subjectType", "USER").param("action", "CHANGE_USER_ROLES"))
                .andExpect(jsonPath("$.items[?(@.subjectId == " + staff.id() + ")]").isNotEmpty()); // 管理员可以复核角色变更。
    }

    private long queueTotal(String path, String state, MockHttpSession actor) throws Exception {
        return read(mvc.perform(get(path).param("status", state).param("pageSize", "1").session(actor))
                .andExpect(status().isOk())).get("total").asLong(); // 读取实际状态队列分页的总量。
    }

    @Test
    void staffCancellationOfPaidOrderPreservesPaymentAndReleasesOnlyItsLock() throws Exception {
        var admin = login("order_admin"); // 管理员负责授权和审核车源。
        var staff = user(); // 用真实的工作人员身份验证后台取消。
        submit(put("/api/admin/users/" + staff.id() + "/roles"), admin, "{\"roles\":[\"STAFF\"]}").andExpect(status().isOk()); // 授予工作人员角色。
        var seller = user(); // 卖家接收交易取消通知。
        var buyer = user(); // 买家保留模拟支付记录及取消原因。
        var car = listed(admin, seller, "DIRECT"); // 准备符合重新上架条件的直销车。
        long id = id(postOrder(buyer, car.id(), token()).andExpect(status().isCreated())); // 下单后还未支付。
        String path = "/api/admin/orders/" + id + "/cancel"; // 后台取消已支付订单的入口。
        String reason = "{\"note\":\" 双方确认无法办理过户 \"}"; // 用带空格的输入验证实际保存的说明。
        submit(post(path), staff.session(), reason).andExpect(status().isConflict()); // 未支付时使用原有买卖双方取消流程。
        long loan = id(submit(post("/api/orders/" + id + "/loan"), buyer.session(), "{\"amount\":20000,\"months\":12}")
                .andExpect(status().isCreated())); // 创建待办理贷款用于核对订单取消联动。
        var payment = read(submit(post("/api/orders/" + id + "/pay"), buyer.session(), null).andExpect(status().isOk())); // 记录实际模拟支付时间。
        submit(post(path), buyer.session(), reason).andExpect(status().isForbidden()); // 买家不能代替后台处理已支付订单。
        submit(post(path), seller.session(), reason).andExpect(status().isForbidden()); // 卖家不能绕过后台权限。
        submit(post(path), staff.session(), "{\"note\":\" \"}").andExpect(status().isBadRequest()); // 必须填写取消原因。
        submit(post(path), staff.session(), reason).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.paidAt").value(payment.get("paidAt").asText()))
                .andExpect(jsonPath("$.completedAt").doesNotExist())
                .andExpect(jsonPath("$.handlingNote").value("双方确认无法办理过户")); // 保留原支付时间、取消原因和未过户事实。
        mvc.perform(get("/api/orders/" + id).session(buyer.session()))
                .andExpect(jsonPath("$.handlingNote").value("双方确认无法办理过户")); // 买方可查后台填写的原因。
        mvc.perform(get("/api/orders/" + id).session(seller.session()))
                .andExpect(jsonPath("$.status").value("CANCELLED")); // 卖方可以查看终态。
        mvc.perform(get("/api/loans/" + loan).session(buyer.session()))
                .andExpect(jsonPath("$.status").value("CANCELLED")); // 未办结贷款同步关闭。
        assertThat(vehicleState(car.id())).isEqualTo("LISTED"); // 符合条件的车源恢复在售。
        assertThat(lockCount(car.id())).isZero(); // 取消事务释放原交易锁。
        long next = id(postOrder(buyer, car.id(), token()).andExpect(status().isCreated())); // 买家可再次下单获取新的锁。
        submit(post(path), staff.session(), reason).andExpect(status().isConflict()); // 终态的旧订单无法再次处理。
        assertThat(jdbc.queryForObject("SELECT order_id FROM vehicle_lock WHERE vehicle_id = ?", Long.class, car.id())).isEqualTo(next); // 核对新锁关系仍然存在。
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE event_key = ? AND receiver_id IN (?, ?)",
                Long.class, "order:" + id + ":cancelled", buyer.id(), seller.id())).isEqualTo(2L); // 各方收到一条取消通知。
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE subject_type = 'ORDER' AND subject_id = ? AND action = 'CANCEL_PAID_ORDER' AND actor_id = ?",
                Long.class, id, staff.id())).isOne(); // 只保存一次后台取消审计。
    }

    @Test
    void staffCancellationOfPaidConsignmentOrderKeepsExpiredVehicleOffline() throws Exception {
        var admin = login("order_admin"); // 准备代销审核与过户人员。
        var seller = user(); // 发布待出售的代销车源。
        var buyer = user(); // 在委托有效期间下单。
        var car = listed(admin, seller, "CONSIGN"); // 建立真实可售代销车及委托。
        long id = id(postOrder(buyer, car.id(), token()).andExpect(status().isCreated())); // 下单锁定尚在有效期的车辆。
        submit(post("/api/orders/" + id + "/pay"), buyer.session(), null).andExpect(status().isOk()); // 订单进入待过户。
        expire(car); // 将临时测试委托调整为已过期。
        submit(post("/api/admin/orders/" + id + "/cancel"), admin, "{\"note\":\"过户取消\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED")); // 后台可关闭已支付旧订单。
        assertThat(vehicleState(car.id())).isEqualTo("OFFLINE"); // 已到期代销车源转为已下架。
        assertThat(lockCount(car.id())).isZero(); // 同时释放车辆占用。
    }

    @Test
    void paidOrderCancellationAndCompletionHaveOneWinner() throws Exception {
        var admin = login("order_admin"); // 一份独立会话用于后台取消。
        var closer = login("order_admin"); // 另一份独立会话用于同时确认成交。
        var seller = user(); // 使用正常的卖家身份发布。
        var buyer = user(); // 完成实际下单和模拟支付。
        var car = listed(admin, seller, "DIRECT"); // 为竞争场景提供独立车源。
        long id = id(postOrder(buyer, car.id(), token()).andExpect(status().isCreated())); // 创建待支付订单。
        submit(post("/api/orders/" + id + "/pay"), buyer.session(), null).andExpect(status().isOk()); // 两个后台动作都从已支付开始。
        String path = "/api/admin/orders/" + id; // 准备两种处理请求。
        var result = race(List.of(withCsrf(post(path + "/cancel"), admin, "{\"note\":\"双方取消过户\"}"),
                withCsrf(post(path + "/complete"), closer, "{\"note\":\"已完成过户\"}"))); // 同时尝试取消与成交。
        assertThat(result.stream().map(item -> item.getResponse().getStatus())).containsExactlyInAnyOrder(200, 409); // 只允许一个操作成功。
        String state = read(mvc.perform(get("/api/orders/" + id).session(buyer.session())).andExpect(status().isOk())).get("status").asText(); // 读取赢家写入的终态。
        assertThat(state).isIn("CANCELLED", "COMPLETED"); // 保留其中一个合法状态。
        assertThat(vehicleState(car.id())).isEqualTo(state.equals("COMPLETED") ? "SOLD" : "LISTED"); // 车辆状态与赢家一致。
        assertThat(lockCount(car.id())).isZero(); // 两个终态都释放同一把车锁。
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
        String username = "order_" + token().replace("-", "").substring(0, 12); // 使用登录接口允许的字符生成独立账号。
        var data = json.createObjectNode().put("username", username).put("password", PASSWORD).put("displayName", username); // 构造普通账号注册数据。
        long userId = id(submit(post("/api/auth/register"), null, data.toString()).andExpect(status().isCreated())); // 经真实认证接口登记并取得用户编号。
        return new TestUser(userId, login(username)); // 生成带有效认证的会话。
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

    private record TestUser(long id, MockHttpSession session) { } // 保存买卖双方的编号和登录会话。
    private record TradeVehicle(long id, Long consignmentId) { } // 保存测试车源和可选代销委托编号。
}
