package cn.edu.usedcar.entity; // 定义 MyBatis-Plus 的表实体。

import java.math.BigDecimal; // 保持交易金额的十进制精度。
import cn.edu.usedcar.model.TradeOrderStatus; // 保存固定的订单状态。
import java.time.LocalDateTime; // 持久化业务操作的北京时间。
import com.baomidou.mybatisplus.annotation.IdType; // 选择实体主键来源。
import com.baomidou.mybatisplus.annotation.TableId; // 标明主键字段。
import com.baomidou.mybatisplus.annotation.TableName; // 绑定实际数据库表。

/** 交易订单历史的持久化映射。
 * @author pangzhihao
 */
@TableName("trade_order") // 绑定现有的数据库表。
public class TradeOrderEntity {
    @TableId(type = IdType.AUTO) // 订单编号由数据库自动分配。
    private Long id; // 保存订单编号。
    private Long vehicleId; // 保存成交车辆编号。
    private Long buyerId; // 保存买家编号。
    private Long sellerId; // 保存卖家编号。
    private Long consignmentId; // 保存下单时有效的委托编号。
    private String requestKey; // 保存该买家的幂等请求标识。
    private BigDecimal amount; // 保存下单时保存的人民币成交价格。
    private TradeOrderStatus status; // 保存当前订单状态。
    private String handlingNote; // 保存工作人员登记的过户说明。
    private LocalDateTime createdAt; // 保存订单创建时间。
    private LocalDateTime paidAt; // 保存模拟支付时间。
    private LocalDateTime completedAt; // 保存过户确认时间。

    public Long getId() {
        return id; // 返回订单编号。
    }

    public void setId(Long id) {
        this.id = id; // 写入订单编号。
    }

    public Long getVehicleId() {
        return vehicleId; // 返回成交车辆编号。
    }

    public void setVehicleId(Long vehicleId) {
        this.vehicleId = vehicleId; // 写入成交车辆编号。
    }

    public Long getBuyerId() {
        return buyerId; // 返回买家编号。
    }

    public void setBuyerId(Long buyerId) {
        this.buyerId = buyerId; // 写入买家编号。
    }

    public Long getSellerId() {
        return sellerId; // 返回卖家编号。
    }

    public void setSellerId(Long sellerId) {
        this.sellerId = sellerId; // 写入卖家编号。
    }

    public Long getConsignmentId() {
        return consignmentId; // 返回下单时有效的委托编号。
    }

    public void setConsignmentId(Long consignmentId) {
        this.consignmentId = consignmentId; // 写入下单时有效的委托编号。
    }

    public String getRequestKey() {
        return requestKey; // 返回该买家的幂等请求标识。
    }

    public void setRequestKey(String requestKey) {
        this.requestKey = requestKey; // 写入该买家的幂等请求标识。
    }

    public BigDecimal getAmount() {
        return amount; // 返回下单时保存的人民币成交价格。
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount; // 写入下单时保存的人民币成交价格。
    }

    public TradeOrderStatus getStatus() {
        return status; // 返回当前订单状态。
    }

    public void setStatus(TradeOrderStatus status) {
        this.status = status; // 写入当前订单状态。
    }

    public String getHandlingNote() {
        return handlingNote; // 返回工作人员登记的过户说明。
    }

    public void setHandlingNote(String handlingNote) {
        this.handlingNote = handlingNote; // 写入工作人员登记的过户说明。
    }

    public LocalDateTime getCreatedAt() {
        return createdAt; // 返回订单创建时间。
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt; // 写入订单创建时间。
    }

    public LocalDateTime getPaidAt() {
        return paidAt; // 返回模拟支付时间。
    }

    public void setPaidAt(LocalDateTime paidAt) {
        this.paidAt = paidAt; // 写入模拟支付时间。
    }

    public LocalDateTime getCompletedAt() {
        return completedAt; // 返回过户确认时间。
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt; // 写入过户确认时间。
    }
}
