package cn.edu.usedcar.mapper;

import java.time.LocalDateTime; // 用于计算待推送时刻和领取期限。
import cn.edu.usedcar.entity.NotificationEntity; // 让收件箱和后台任务共用实体。
import com.baomidou.mybatisplus.core.mapper.BaseMapper; // 复用分页和查询方法。
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select; // 读取用于计划任务的数据库时间。
import org.apache.ibatis.annotations.Update; // 以条件更新保证推送和标记已读幂等。

/**
 * 通知持久层。数据库唯一键按接收人与事件标识保证幂等。
 * @author pangzhihao
 */
public interface NotificationMapper extends BaseMapper<NotificationEntity> { // 基础查询由 MyBatis-Plus 提供。
    /** 重复事件保留原消息内容、已读和推送状态。 */
    @Insert("""
            INSERT INTO notification (receiver_id, vehicle_id, event_key, subject_type, subject_id, message)
            VALUES (#{receiverId}, #{vehicleId,jdbcType=BIGINT}, #{eventKey}, #{subjectType}, #{subjectId}, #{message})
            ON DUPLICATE KEY UPDATE event_key = notification.event_key
            """)
    void insertOnce(@Param("receiverId") long receiverId, @Param("vehicleId") Long vehicleId,
                    @Param("eventKey") String eventKey, @Param("subjectType") String subjectType,
                    @Param("subjectId") long subjectId, @Param("message") String message);

    @Update("UPDATE notification SET read_at = CURRENT_TIMESTAMP WHERE id = #{id} AND receiver_id = #{receiverId} AND read_at IS NULL") // 原子保留第一次已读时间。
    int markRead(@Param("id") long id, @Param("receiverId") long receiverId); // 双重限定通知编号与接收人。

    @Select("SELECT CURRENT_TIMESTAMP") // 使用与通知默认字段一致的数据库时钟。
    LocalDateTime databaseNow(); // 避免应用和数据库时区不同导致提前或延后重试。

    @Update("""
            UPDATE notification SET push_attempts = push_attempts + 1, next_attempt_at = #{leaseUntil}
            WHERE id = #{id} AND push_attempts = #{attempts} AND pushed_at IS NULL
              AND read_at IS NULL AND next_attempt_at <= CURRENT_TIMESTAMP
            """)
    int claim(@Param("id") long id, @Param("attempts") int attempts,
              @Param("leaseUntil") LocalDateTime leaseUntil); // 领取到期未读消息，推送任务中断时租约会自然失效。

    @Update("""
            UPDATE notification SET pushed_at = CURRENT_TIMESTAMP
            WHERE id = #{id} AND push_attempts = #{attempt} AND pushed_at IS NULL
            """)
    int confirmPush(@Param("id") long id, @Param("attempt") int attempt); // 只有仍持有本次领取版本时才记录成功。

    @Update("""
            UPDATE notification SET next_attempt_at = #{when}
            WHERE id = #{id} AND push_attempts = #{attempt} AND pushed_at IS NULL
            """)
    int postponePush(@Param("id") long id, @Param("attempt") int attempt,
                     @Param("when") LocalDateTime when); // 失败后安排同一条通知重试。
}
