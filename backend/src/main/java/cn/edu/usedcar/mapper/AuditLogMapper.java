package cn.edu.usedcar.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

/**
 * 审计日志的数据访问接口。系统初始化等操作允许没有操作人账号。
 * @author pangzhihao
 */
public interface AuditLogMapper {
    @Insert("""
            INSERT INTO audit_log (actor_id, action, subject_type, subject_id, detail)
            VALUES (#{actorId,jdbcType=BIGINT}, #{action}, #{subjectType}, #{subjectId}, #{detail})
            """)
    void insert(@Param("actorId") Long actorId, @Param("action") String action,
                @Param("subjectType") String subjectType, @Param("subjectId") long subjectId,
                @Param("detail") String detail);
}
