package cn.edu.usedcar.service; // 将已经提交的站内通知转化为在线刷新信号。

import java.time.LocalDateTime; // 使用数据库时钟计算领取期限与下一次重试。
import java.util.List; // 保存同一用户本轮待推送记录。
import java.util.stream.Collectors; // 以接收人归并消息提示。
import cn.edu.usedcar.entity.NotificationEntity; // 读取通知的派发字段。
import cn.edu.usedcar.mapper.NotificationMapper; // 领取、确认和重新安排已持久化的事件。
import cn.edu.usedcar.websocket.NotificationSocketHandler; // 检查在线会话并发送同账号信号。
import com.baomidou.mybatisplus.core.toolkit.Wrappers; // 用参数化条件查找可投递记录。
import com.baomidou.mybatisplus.extension.plugins.pagination.Page; // 每次调度读取有上限的待处理记录。
import org.slf4j.Logger; // 记录可用于排查的刷新失败。
import org.slf4j.LoggerFactory; // 取得本服务的日志记录器。
import org.springframework.beans.factory.annotation.Value; // 从配置读取推送启用开关。
import org.springframework.scheduling.annotation.Scheduled; // 定期尝试投递在线通知。
import org.springframework.stereotype.Service; // 注册可靠通知调度服务。

/** 同一通知行直接承担在线派发、领取租约与失败重试。
 * @author pangzhihao
 */
@Service // 在独立的计划任务中读取已经提交的业务变更。
public class NotificationPushService {
    private static final Logger LOG = LoggerFactory.getLogger(NotificationPushService.class); // 记录投递管道级错误。
    private final NotificationMapper notifications; // 在数据库保存认领与重试结果。
    private final NotificationSocketHandler sockets; // 发送由浏览器触发的轻量刷新信号。
    private final boolean schedulingEnabled; // 便于专用测试关闭定时任务并手动验证。

    public NotificationPushService(NotificationMapper notifications, NotificationSocketHandler sockets,
                                   @Value("${app.notifications.dispatch-enabled:true}") boolean schedulingEnabled) {
        this.notifications = notifications; // 保存通知持久层。
        this.sockets = sockets; // 保存在线连接注册表。
        this.schedulingEnabled = schedulingEnabled; // 保存调度开关。
    }

    @Scheduled(fixedDelayString = "${app.notifications.dispatch-delay-ms:1500}")
    void scheduledDispatch() {
        if (schedulingEnabled) dispatchDue(); // 后台轮询独立于业务写事务，只能读取已提交的通知记录。
    }

    public void dispatchDue() {
        var online = sockets.receivers(); // 获取这个节点当前在线的接收人。
        if (online.isEmpty()) return; // 离线期间保留数据，等连接恢复后发送提示。
        var now = notifications.databaseNow(); // 等待期限使用与原始时间字段一致的数据库时钟。
        var page = notifications.selectPage(new Page<NotificationEntity>(1, 64, false), Wrappers.lambdaQuery(NotificationEntity.class)
                .in(NotificationEntity::getReceiverId, online) // 调度只领取目前能够推送的接收人。
                .isNull(NotificationEntity::getPushedAt).isNull(NotificationEntity::getReadAt) // 已推送或已阅读时不需要再提醒。
                .le(NotificationEntity::getNextAttemptAt, now) // 遵守失败退避和分布式领取期限。
                .orderByAsc(NotificationEntity::getNextAttemptAt, NotificationEntity::getId)); // 先处理原本较早到期的事项。
        var recipients = page.getRecords().stream().collect(Collectors.groupingBy(NotificationEntity::getReceiverId)); // 同一接收人只需收到一次刷新提示。
        recipients.forEach((receiverId, entries) -> send(receiverId, entries, now)); // 逐个账号隔离发送。
    }

    private void send(long receiverId, List<NotificationEntity> entries, LocalDateTime now) {
        var claimed = entries.stream()
                .filter(row -> notifications.claim(row.getId(), row.getPushAttempts(), now.plusSeconds(30)) == 1)
                .toList(); // 写入有期限的领取记录，其他任务正在投递时跳过。
        if (claimed.isEmpty()) return; // 没有实际获得领取权，就无需发送提示。
        boolean sent;
        try {
            sent = sockets.signal(receiverId); // 把刷新提示交给目标账号的登录连接。
        } catch (RuntimeException failure) {
            LOG.warn("Notification signal failed for receiver {}", receiverId, failure); // 不把消息文本写进应用日志。
            sent = false; // 保留本轮领取记录，稍后重试。
        }
        var failureTime = sent ? null : notifications.databaseNow(); // 退避间隔从实际失败时刻开始计算。
        for (var row : claimed) {
            int attempt = row.getPushAttempts() + 1; // 取当前已经持久化的领取版本。
            if (sent) notifications.confirmPush(row.getId(), attempt); // 网络提交成功时写入推送时间。
            else notifications.postponePush(row.getId(), attempt, failureTime.plusSeconds(Math.min(60, 1 << Math.min(attempt, 6)))); // 失败时用有上限的指数退避重试原记录。
        }
    }
}
