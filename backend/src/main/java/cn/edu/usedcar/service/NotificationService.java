package cn.edu.usedcar.service;

import java.time.LocalDateTime; // 以数据库时间展示通知和已读时间。
import java.util.List; // 返回已经限定接收人的通知页。
import cn.edu.usedcar.common.ApiException; // 统一隐藏越权或不存在的通知。
import cn.edu.usedcar.entity.NotificationEntity; // 用于分页筛选和通知视图转换。
import cn.edu.usedcar.mapper.NotificationMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers; // 使用带参数的条件构造器。
import org.springframework.http.HttpStatus; // 指定无权或缺失时返回 404。
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation; // 公开分页和未读数量来自同一快照。
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 负责站内通知登记、个人收件箱和已读操作。
 * @author pangzhihao
 */
@Service
public class NotificationService {
    private final NotificationMapper notifications;

    public NotificationService(NotificationMapper notifications) {
        this.notifications = notifications;
    }

    /** 业务失败时通知跟随事务回滚。相同事件重试时由持久层保留原通知。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(long receiverId, Long vehicleId, String eventKey, String subjectType,
                       long subjectId, String message) {
        notifications.insertOnce(receiverId, vehicleId, eventKey, subjectType, subjectId, message);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ) // 列表和未读汇总使用同一数据库快照。
    public Inbox list(long receiverId, boolean unreadOnly, int page, int pageSize) {
        var filter = Wrappers.lambdaQuery(NotificationEntity.class)
                .eq(NotificationEntity::getReceiverId, receiverId) // 每次查询严格限定会话账号。
                .isNull(unreadOnly, NotificationEntity::getReadAt) // 可选择只读取尚未标记的通知。
                .orderByDesc(NotificationEntity::getId); // 新通知优先，分页顺序一致。
        var rows = notifications.selectPage(BookingSupport.page(page, pageSize), filter); // 由 MyBatis-Plus 分页并计算总量。
        long unread = unreadOnly ? rows.getTotal() : notifications.selectCount(Wrappers.lambdaQuery(NotificationEntity.class)
                .eq(NotificationEntity::getReceiverId, receiverId).isNull(NotificationEntity::getReadAt)); // 无论选择哪个页都返回本人当前未读数。
        return new Inbox(rows.getRecords().stream().map(NotificationService::view).toList(),
                rows.getTotal(), page, pageSize, unread); // 返回授权范围内的公开通知视图。
    }

    @Transactional // 统一校验归属并保证重复请求不会重写已读时间。
    public void markRead(long id, long receiverId) {
        if (notifications.markRead(id, receiverId) == 1) return; // 初次阅读时由数据库写入时刻。
        if (notifications.selectCount(Wrappers.lambdaQuery(NotificationEntity.class)
                .eq(NotificationEntity::getId, id).eq(NotificationEntity::getReceiverId, receiverId)) == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND", "通知不存在或不可查看。"); // 不向其他账号泄露存在性。
        }
    }

    private static View view(NotificationEntity row) {
        return new View(row.getId(), row.getVehicleId(), row.getSubjectType(), row.getSubjectId(), row.getMessage(),
                row.getReadAt(), row.getCreatedAt()); // 内部事件标识、领取次数和重试计划不返回给浏览器。
    }

    /** 分页通知与同一用户的未读汇总。 */
    public record Inbox(List<View> items, long total, int page, int pageSize, long unreadCount) { } // 兼容现有前端分页字段。

    /** 站内收件箱对接收方公开的字段。 */
    public record View(
            long id, // 通知编号，用于标记已读。
            Long vehicleId, // 跳转业务所需的关联车辆。
            String subjectType, // 业务类别。
            long subjectId, // 具体业务记录编号。
            String message, // 给本人展示的正文。
            LocalDateTime readAt, // 首次标记已读时间。
            LocalDateTime createdAt // 通知入库时间。
    ) { }
}
