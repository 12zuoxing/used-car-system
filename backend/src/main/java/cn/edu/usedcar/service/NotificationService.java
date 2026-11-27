package cn.edu.usedcar.service;

import cn.edu.usedcar.mapper.NotificationMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 负责业务事务内的站内通知登记，可供后续交易与送修模块复用。
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
}
