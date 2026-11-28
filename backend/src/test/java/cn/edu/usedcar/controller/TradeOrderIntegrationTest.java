package cn.edu.usedcar.controller; // 运行订单流程在 H2 上的快速回归。

import org.springframework.boot.test.context.SpringBootTest; // 通过完整应用上下文测试 HTTP 接口。

/** 日常构建执行的订单集成测试。
 * @author pangzhihao
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:order-module;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", // 为订单用例提供独立测试库。
        "spring.datasource.username=sa", "spring.datasource.password=", // 使用 H2 内存库的初始用户。
        "app.admin.username=order_admin", "app.admin.password=local-order-test-password", // 初始化测试审核员。
        "app.admin.display-name=订单测试管理员" // 用清晰名称识别测试账号。
})
class TradeOrderIntegrationTest extends TradeOrderWorkflowTests { } // 复用直销、代销与并发场景。
