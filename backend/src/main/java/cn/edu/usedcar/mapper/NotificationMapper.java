package cn.edu.usedcar.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

/**
 * 通知持久层。数据库唯一键按接收人与事件标识保证幂等。
 * @author pangzhihao
 */
public interface NotificationMapper {
    /** 重复事件保留原消息内容、已读和推送状态。 */
    @Insert("""
            INSERT INTO notification (receiver_id, vehicle_id, event_key, subject_type, subject_id, message)
            VALUES (#{receiverId}, #{vehicleId,jdbcType=BIGINT}, #{eventKey}, #{subjectType}, #{subjectId}, #{message})
            ON DUPLICATE KEY UPDATE event_key = notification.event_key
            """)
    void insertOnce(@Param("receiverId") long receiverId, @Param("vehicleId") Long vehicleId,
                    @Param("eventKey") String eventKey, @Param("subjectType") String subjectType,
                    @Param("subjectId") long subjectId, @Param("message") String message);
}
