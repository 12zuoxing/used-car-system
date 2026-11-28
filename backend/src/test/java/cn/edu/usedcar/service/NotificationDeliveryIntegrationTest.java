package cn.edu.usedcar.service; // 使用真实通知表测试投递生命周期。

import java.time.LocalDateTime; // 核查重试计划与推送成功时间。
import java.util.Set; // 设置当前模拟的在线接收人集合。
import java.util.UUID; // 为测试数据生成独立账号与通知标识。
import cn.edu.usedcar.websocket.NotificationSocketHandler; // 将不可靠网络替换为可控的发送结果。
import org.junit.jupiter.api.Test; // 声明端到端持久化测试。
import org.springframework.beans.factory.annotation.Autowired; // 注入真实事务和数据访问依赖。
import org.springframework.boot.test.context.SpringBootTest; // 启动现有应用配置。
import org.springframework.jdbc.core.JdbcTemplate; // 查询测试通知的实际持久化字段。
import org.springframework.test.context.bean.override.mockito.MockitoBean; // 只替换 WebSocket 传输边界。
import org.springframework.transaction.support.TransactionTemplate; // 用业务事务登记通知。

import static org.assertj.core.api.Assertions.assertThat; // 验证数据状态和重试间隔。
import static org.mockito.Mockito.times; // 核对实际唤醒次数。
import static org.mockito.Mockito.verify; // 检查是否向在线接收人发送提示。
import static org.mockito.Mockito.when; // 模拟断线、发送失败、重新连接过程。

/** 保留真实的唯一索引、事务和后台调度逻辑，仅模拟网络结果。
 * @author pangzhihao
 */
@SpringBootTest(properties = {
        "spring.datasource.url=${ORDER_TEST_MYSQL_URL:jdbc:h2:mem:notice-delivery;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1}", // 默认 H2，提供环境变量时使用隔离 MySQL。
        "spring.datasource.username=${ORDER_TEST_MYSQL_USER:sa}", // 使用测试账号。
        "spring.datasource.password=${ORDER_TEST_MYSQL_PASSWORD:}", // 保留独立数据库的认证设置。
        "app.admin.username=", "app.admin.password=", // 不向测试投递场景自动添加管理员。
        "app.notifications.dispatch-enabled=false" // 测试精确控制调度触发时间。
})
class NotificationDeliveryIntegrationTest {
    @Autowired private AuthService auth; // 通过真实注册创建通知接收者。
    @Autowired private NotificationService notifications; // 使用与业务变更一致的通知登记入口。
    @Autowired private NotificationPushService delivery; // 调用实际的推送领取和重试服务。
    @Autowired private JdbcTemplate jdbc; // 从数据库校验最终通知记录。
    @Autowired private TransactionTemplate transactions; // 让通知在提交后才获得可见性。
    @MockitoBean private NotificationSocketHandler sockets; // 只替换在线状态和网络写入。

    @Test
    void retryPreservesTheOriginalNotificationAndSkipsOfflineAndReadRows() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10); // 避免测试重复运行时出现重名。
        long receiver = auth.register("push_" + suffix, "test-message-password", "通知接收人").id(); // 创建真实接收账号。
        transactions.executeWithoutResult(state -> {
            notifications.record(receiver, null, suffix, "ORDER", 7L, "第一条通知"); // 登记待投递事件。
            notifications.record(receiver, null, suffix, "ORDER", 7L, "重复请求不可覆盖正文"); // 重试仍然使用业务事件键。
            notifications.record(receiver, null, "already-read-" + suffix, "ORDER", 8L, "已阅读的通知"); // 另一条用于验证已读不发送。
        });
        long notice = jdbc.queryForObject("SELECT id FROM notification WHERE receiver_id = ? AND event_key = ?", Long.class,
                receiver, suffix); // 找到始终用于重试的持久化行。
        long read = jdbc.queryForObject("SELECT id FROM notification WHERE receiver_id = ? AND event_key = ?", Long.class,
                receiver, "already-read-" + suffix); // 找到预先阅读的通知行。
        notifications.markRead(read, receiver); // 在连接前通过正常服务标记已读。
        when(sockets.receivers()).thenReturn(Set.of()); // 模拟客户离线。
        delivery.dispatchDue(); // 调度不能消费离线账号持有的行。
        assertThat(attempts(notice)).isZero(); // 保留待投递状态。
        when(sockets.receivers()).thenReturn(Set.of(receiver)); // 模拟客户连接或重新登录。
        when(sockets.signal(receiver)).thenReturn(false, true); // 首次传输失败，第二次成功。
        var before = jdbc.queryForObject("SELECT CURRENT_TIMESTAMP", LocalDateTime.class); // 记录失败之前的数据库时钟。
        delivery.dispatchDue(); // 实际领取并尝试推送。
        assertThat(attempts(notice)).isEqualTo(1); // 第一次失败持久化尝试次数。
        assertThat(jdbc.queryForObject("SELECT pushed_at FROM notification WHERE id = ?", LocalDateTime.class, notice)).isNull(); // 失败不能显示成已推送。
        assertThat(jdbc.queryForObject("SELECT next_attempt_at FROM notification WHERE id = ?", LocalDateTime.class, notice))
                .isAfter(before); // 计算了晚于当前的重试时间。
        jdbc.update("UPDATE notification SET next_attempt_at = CURRENT_TIMESTAMP WHERE id = ?", notice); // 让测试模拟退避已结束，省去等待。
        delivery.dispatchDue(); // 重试已有记录。
        delivery.dispatchDue(); // 已推送的事件不会重复发送。
        verify(sockets, times(2)).signal(receiver); // 两次实际尝试只对应同一个业务事件。
        assertThat(attempts(notice)).isEqualTo(2); // 同一行中连续记录两次尝试。
        assertThat(attempts(read)).isZero(); // 已阅读的同账号消息不占用投递。
        assertThat(jdbc.queryForObject("SELECT pushed_at FROM notification WHERE id = ?", LocalDateTime.class, notice)).isNotNull(); // 成功后持久化在线提示时间。
        assertThat(jdbc.queryForObject("SELECT message FROM notification WHERE id = ?", String.class, notice)).isEqualTo("第一条通知"); // 去重时保留原正文。
    }

    private int attempts(long id) {
        return jdbc.queryForObject("SELECT push_attempts FROM notification WHERE id = ?", Integer.class, id); // 查询本条通知实际领取次数。
    }
}
