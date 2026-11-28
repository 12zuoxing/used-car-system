package cn.edu.usedcar.entity; // 定义贷款表的持久化映射。

import java.math.BigDecimal; // 按十进制保存金额。
import java.time.LocalDateTime; // 映射数据库的本地时间字段。
import cn.edu.usedcar.model.LoanStatus; // 限制实体状态的合法取值。
import com.baomidou.mybatisplus.annotation.FieldStrategy; // 允许清空已经保存的可选字段。
import com.baomidou.mybatisplus.annotation.IdType; // 使用数据库的自增策略。
import com.baomidou.mybatisplus.annotation.TableField; // 声明字段更新策略。
import com.baomidou.mybatisplus.annotation.TableId; // 配置实体主键。
import com.baomidou.mybatisplus.annotation.TableName; // 绑定数据库表。

/** 贷款申请实体，映射已有的 loan_application 表。
 * @author pangzhihao
 */
@TableName("loan_application") // 使用已有贷款申请表。
public class LoanApplicationEntity {
    @TableId(type = IdType.AUTO) // 新申请由数据库分配编号。
    private Long id; // 保存申请自增编号。
    private Long orderId; // 保存绑定的交易订单编号。
    private Long applicantId; // 保存提交申请的买家编号。
    private Long handlerId; // 保存处理资料的工作人员编号。
    private BigDecimal amount; // 保存按人民币保存的申请金额。
    private Integer months; // 保存约定的办理期数。
    @TableField(updateStrategy = FieldStrategy.ALWAYS) // 更新时允许将文字字段清空。
    private String note; // 保存买家提交的登记说明。
    private LoanStatus status; // 保存当前办理状态。
    @TableField(updateStrategy = FieldStrategy.ALWAYS) // 更新时允许将文字字段清空。
    private String resultNote; // 保存最近一条工作人员处理说明。
    private LocalDateTime createdAt; // 保存首次提交的北京时间。

    public Long getId() {
        return id; // 返回申请自增编号。
    }

    public void setId(Long id) {
        this.id = id; // 写入申请自增编号。
    }

    public Long getOrderId() {
        return orderId; // 返回绑定的交易订单编号。
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId; // 写入绑定的交易订单编号。
    }

    public Long getApplicantId() {
        return applicantId; // 返回提交申请的买家编号。
    }

    public void setApplicantId(Long applicantId) {
        this.applicantId = applicantId; // 写入提交申请的买家编号。
    }

    public Long getHandlerId() {
        return handlerId; // 返回处理资料的工作人员编号。
    }

    public void setHandlerId(Long handlerId) {
        this.handlerId = handlerId; // 写入处理资料的工作人员编号。
    }

    public BigDecimal getAmount() {
        return amount; // 返回按人民币保存的申请金额。
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount; // 写入按人民币保存的申请金额。
    }

    public Integer getMonths() {
        return months; // 返回约定的办理期数。
    }

    public void setMonths(Integer months) {
        this.months = months; // 写入约定的办理期数。
    }

    public String getNote() {
        return note; // 返回买家提交的登记说明。
    }

    public void setNote(String note) {
        this.note = note; // 写入买家提交的登记说明。
    }

    public LoanStatus getStatus() {
        return status; // 返回当前办理状态。
    }

    public void setStatus(LoanStatus status) {
        this.status = status; // 写入当前办理状态。
    }

    public String getResultNote() {
        return resultNote; // 返回最近一条工作人员处理说明。
    }

    public void setResultNote(String resultNote) {
        this.resultNote = resultNote; // 写入最近一条工作人员处理说明。
    }

    public LocalDateTime getCreatedAt() {
        return createdAt; // 返回首次提交的北京时间。
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt; // 写入首次提交的北京时间。
    }
}
