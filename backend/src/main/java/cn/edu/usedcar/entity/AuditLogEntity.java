package cn.edu.usedcar.entity; // 定义 MyBatis-Plus 访问的持久化模型。

import java.time.LocalDateTime; // 映射数据库记录的日期和时间。
import com.baomidou.mybatisplus.annotation.IdType; // 声明主键生成方式。
import com.baomidou.mybatisplus.annotation.TableId; // 声明实体主键。
import com.baomidou.mybatisplus.annotation.TableName; // 绑定已有数据库表。

/** 审计日志的持久化映射。
 * @author pangzhihao
 */
@TableName("audit_log") // 使用已有结构和索引。
public class AuditLogEntity {
    @TableId(type = IdType.AUTO) // 使用数据库生成的主键。
    private Long id; // 保存审计日志编号。
    private Long actorId; // 保存执行操作的账号编号。
    private String action; // 保存操作事件代码。
    private String subjectType; // 保存被操作的业务类型。
    private Long subjectId; // 保存被操作的业务记录编号。
    private String detail; // 保存管理员权限下可查询的审计说明。
    private LocalDateTime createdAt; // 保存操作留痕的数据库时间。

    public Long getId() {
        return id; // 返回审计日志编号。
    }

    public void setId(Long id) {
        this.id = id; // 写入审计日志编号。
    }

    public Long getActorId() {
        return actorId; // 返回执行操作的账号编号。
    }

    public void setActorId(Long actorId) {
        this.actorId = actorId; // 写入执行操作的账号编号。
    }

    public String getAction() {
        return action; // 返回操作事件代码。
    }

    public void setAction(String action) {
        this.action = action; // 写入操作事件代码。
    }

    public String getSubjectType() {
        return subjectType; // 返回被操作的业务类型。
    }

    public void setSubjectType(String subjectType) {
        this.subjectType = subjectType; // 写入被操作的业务类型。
    }

    public Long getSubjectId() {
        return subjectId; // 返回被操作的业务记录编号。
    }

    public void setSubjectId(Long subjectId) {
        this.subjectId = subjectId; // 写入被操作的业务记录编号。
    }

    public String getDetail() {
        return detail; // 返回管理员权限下可查询的审计说明。
    }

    public void setDetail(String detail) {
        this.detail = detail; // 写入管理员权限下可查询的审计说明。
    }

    public LocalDateTime getCreatedAt() {
        return createdAt; // 返回操作留痕的数据库时间。
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt; // 写入操作留痕的数据库时间。
    }
}
