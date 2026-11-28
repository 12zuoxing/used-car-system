package cn.edu.usedcar.entity; // 定义持久层使用的表实体。

import java.math.BigDecimal; // 保持费用的十进制精度。
import java.time.LocalDateTime; // 与业务时区的数据库时间映射。
import cn.edu.usedcar.model.ConsignmentStatus; // 将数据库状态映射成枚举。
import com.baomidou.mybatisplus.annotation.IdType; // 选择数据库的自增主键策略。
import com.baomidou.mybatisplus.annotation.TableId; // 标注实体主键。
import com.baomidou.mybatisplus.annotation.TableName; // 绑定数据库表。

/** 代销委托表实体，供 MyBatis-Plus 读写现有表。
 * @author pangzhihao
 */
@TableName("consignment") // 指定对应的数据表。
public class ConsignmentEntity {
    @TableId(type = IdType.AUTO) // 让数据库分配新记录的编号。
    private Long id; // 委托记录的自增主键。
    private Long vehicleId; // 受托车源编号。
    private Long ownerId; // 提交委托的车主编号。
    private LocalDateTime startsAt; // 委托可生效的起始时间。
    private LocalDateTime expiresAt; // 委托截止时间。
    private BigDecimal fee; // 人民币计价的服务费。
    private String terms; // 卖家填写的委托约定。
    private ConsignmentStatus status; // 委托保存的审核状态。
    private Long reviewerId; // 经办审核的账号编号。
    private String reviewNote; // 工作人员给出的审核说明。
    private LocalDateTime reviewedAt; // 工作人员实际审核时间。
    private LocalDateTime createdAt; // 委托首次提交时间。

    public Long getId() {
        return id; // 返回委托记录的自增主键。
    }

    public void setId(Long id) {
        this.id = id; // 写入委托记录的自增主键。
    }

    public Long getVehicleId() {
        return vehicleId; // 返回受托车源编号。
    }

    public void setVehicleId(Long vehicleId) {
        this.vehicleId = vehicleId; // 写入受托车源编号。
    }

    public Long getOwnerId() {
        return ownerId; // 返回提交委托的车主编号。
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId; // 写入提交委托的车主编号。
    }

    public LocalDateTime getStartsAt() {
        return startsAt; // 返回委托可生效的起始时间。
    }

    public void setStartsAt(LocalDateTime startsAt) {
        this.startsAt = startsAt; // 写入委托可生效的起始时间。
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt; // 返回委托截止时间。
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt; // 写入委托截止时间。
    }

    public BigDecimal getFee() {
        return fee; // 返回人民币计价的服务费。
    }

    public void setFee(BigDecimal fee) {
        this.fee = fee; // 写入人民币计价的服务费。
    }

    public String getTerms() {
        return terms; // 返回卖家填写的委托约定。
    }

    public void setTerms(String terms) {
        this.terms = terms; // 写入卖家填写的委托约定。
    }

    public ConsignmentStatus getStatus() {
        return status; // 返回委托保存的审核状态。
    }

    public void setStatus(ConsignmentStatus status) {
        this.status = status; // 写入委托保存的审核状态。
    }

    public Long getReviewerId() {
        return reviewerId; // 返回经办审核的账号编号。
    }

    public void setReviewerId(Long reviewerId) {
        this.reviewerId = reviewerId; // 写入经办审核的账号编号。
    }

    public String getReviewNote() {
        return reviewNote; // 返回工作人员给出的审核说明。
    }

    public void setReviewNote(String reviewNote) {
        this.reviewNote = reviewNote; // 写入工作人员给出的审核说明。
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt; // 返回工作人员实际审核时间。
    }

    public void setReviewedAt(LocalDateTime reviewedAt) {
        this.reviewedAt = reviewedAt; // 写入工作人员实际审核时间。
    }

    public LocalDateTime getCreatedAt() {
        return createdAt; // 返回委托首次提交时间。
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt; // 写入委托首次提交时间。
    }
}
