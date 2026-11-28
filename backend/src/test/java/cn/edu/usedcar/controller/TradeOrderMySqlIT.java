package cn.edu.usedcar.controller; // 运行订单流程在真实 MySQL 上的验证。

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable; // 仅在提供独立 MySQL 测试库时执行。
import org.springframework.boot.test.context.SpringBootTest; // 通过完整应用上下文发起接口请求。

/** 使用环境变量指定的专用 MySQL 库运行订单集成测试。
 * @author pangzhihao
 */
@EnabledIfEnvironmentVariable(named = "ORDER_TEST_MYSQL_URL", matches = "jdbc:mysql:.+") // 要求明确提供 MySQL 地址。
@SpringBootTest(properties = {
        "spring.datasource.url=${ORDER_TEST_MYSQL_URL}", // 测试数据只写入专门指定的数据库。
        "spring.datasource.username=${ORDER_TEST_MYSQL_USER:root}", // 从测试环境获取用户名。
        "spring.datasource.password=${ORDER_TEST_MYSQL_PASSWORD:}", // 从测试环境获取密码。
        "app.admin.username=order_admin", "app.admin.password=local-order-test-password", // 初始化测试用的业务审核账号。
        "app.admin.display-name=订单测试管理员" // 为测试审核员指定页面名称。
})
class TradeOrderMySqlIT extends TradeOrderWorkflowTests { } // 让真实 MySQL 重跑同一套状态和并发验收。
