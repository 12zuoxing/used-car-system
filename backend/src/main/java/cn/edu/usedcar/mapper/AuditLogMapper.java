package cn.edu.usedcar.mapper;

import cn.edu.usedcar.entity.AuditLogEntity; // 将审计读写映射到原审计表。
import com.baomidou.mybatisplus.core.mapper.BaseMapper; // 复用有条件的分页查询能力。
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

/**
 * 审计日志的数据访问接口。系统初始化等操作允许没有操作人账号。
 * @author pangzhihao
 */
public interface AuditLogMapper extends BaseMapper<AuditLogEntity> { // 保留原事务内插入接口。
    @Insert("""
            INSERT INTO audit_log (actor_id, action, subject_type, subject_id, detail)
            VALUES (#{actorId,jdbcType=BIGINT}, #{action}, #{subjectType}, #{subjectId}, #{detail})
            """)
    void insertEvent(@Param("actorId") Long actorId, @Param("action") String action, // 保留带数据库入库时间的显式事件写入。
                @Param("subjectType") String subjectType, @Param("subjectId") long subjectId,
                @Param("detail") String detail);
}
