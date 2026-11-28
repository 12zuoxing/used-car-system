package cn.edu.usedcar.entity; // 定义 MyBatis-Plus 访问的持久化模型。

import java.time.LocalDateTime; // 映射数据库记录的日期和时间。
import com.baomidou.mybatisplus.annotation.IdType; // 声明主键生成方式。
import com.baomidou.mybatisplus.annotation.TableId; // 声明实体主键。
import com.baomidou.mybatisplus.annotation.TableName; // 绑定已有数据库表。

/** 站内通知和推送进度的持久化映射。
 * @author pangzhihao
 */
@TableName("notification") // 使用已有结构和索引。
public class NotificationEntity {
    @TableId(type = IdType.AUTO) // 使用数据库生成的主键。
    private Long id; // 保存通知自增编号。
    private Long receiverId; // 保存唯一接收账号编号。
    private Long vehicleId; // 保存关联车辆编号。
    private String eventKey; // 保存业务事件去重标识。
    private String subjectType; // 保存关联业务类型。
    private Long subjectId; // 保存关联业务记录编号。
    private String message; // 保存允许接收用户查看的消息内容。
    private LocalDateTime readAt; // 保存用户首次标为已读的时间。
    private LocalDateTime pushedAt; // 保存在线提示的成功提交时间。
    private Integer pushAttempts; // 保存已经领取的推送次数。
    private LocalDateTime nextAttemptAt; // 保存再次领取推送的允许时刻。
    private LocalDateTime createdAt; // 保存通知入库时间。

    public Long getId() {
        return id; // 返回通知自增编号。
    }

    public void setId(Long id) {
        this.id = id; // 写入通知自增编号。
    }

    public Long getReceiverId() {
        return receiverId; // 返回唯一接收账号编号。
    }

    public void setReceiverId(Long receiverId) {
        this.receiverId = receiverId; // 写入唯一接收账号编号。
    }

    public Long getVehicleId() {
        return vehicleId; // 返回关联车辆编号。
    }

    public void setVehicleId(Long vehicleId) {
        this.vehicleId = vehicleId; // 写入关联车辆编号。
    }

    public String getEventKey() {
        return eventKey; // 返回业务事件去重标识。
    }

    public void setEventKey(String eventKey) {
        this.eventKey = eventKey; // 写入业务事件去重标识。
    }

    public String getSubjectType() {
        return subjectType; // 返回关联业务类型。
    }

    public void setSubjectType(String subjectType) {
        this.subjectType = subjectType; // 写入关联业务类型。
    }

    public Long getSubjectId() {
        return subjectId; // 返回关联业务记录编号。
    }

    public void setSubjectId(Long subjectId) {
        this.subjectId = subjectId; // 写入关联业务记录编号。
    }

    public String getMessage() {
        return message; // 返回允许接收用户查看的消息内容。
    }

    public void setMessage(String message) {
        this.message = message; // 写入允许接收用户查看的消息内容。
    }

    public LocalDateTime getReadAt() {
        return readAt; // 返回用户首次标为已读的时间。
    }

    public void setReadAt(LocalDateTime readAt) {
        this.readAt = readAt; // 写入用户首次标为已读的时间。
    }

    public LocalDateTime getPushedAt() {
        return pushedAt; // 返回在线提示的成功提交时间。
    }

    public void setPushedAt(LocalDateTime pushedAt) {
        this.pushedAt = pushedAt; // 写入在线提示的成功提交时间。
    }

    public Integer getPushAttempts() {
        return pushAttempts; // 返回已经领取的推送次数。
    }

    public void setPushAttempts(Integer pushAttempts) {
        this.pushAttempts = pushAttempts; // 写入已经领取的推送次数。
    }

    public LocalDateTime getNextAttemptAt() {
        return nextAttemptAt; // 返回再次领取推送的允许时刻。
    }

    public void setNextAttemptAt(LocalDateTime nextAttemptAt) {
        this.nextAttemptAt = nextAttemptAt; // 写入再次领取推送的允许时刻。
    }

    public LocalDateTime getCreatedAt() {
        return createdAt; // 返回通知入库时间。
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt; // 写入通知入库时间。
    }
}
