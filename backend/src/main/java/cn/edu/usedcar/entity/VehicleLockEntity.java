package cn.edu.usedcar.entity; // 定义 MyBatis-Plus 的表实体。

import java.time.LocalDateTime; // 持久化业务操作的北京时间。
import com.baomidou.mybatisplus.annotation.IdType; // 选择实体主键来源。
import com.baomidou.mybatisplus.annotation.TableId; // 标明主键字段。
import com.baomidou.mybatisplus.annotation.TableName; // 绑定实际数据库表。

/** 当前车辆占用锁的持久化映射。
 * @author pangzhihao
 */
@TableName("vehicle_lock") // 绑定现有的数据库表。
public class VehicleLockEntity {
    @TableId(type = IdType.INPUT) // 直接用车源编号标记同车唯一的锁。
    private Long vehicleId; // 保存唯一的被锁车辆编号。
    private Long orderId; // 保存实际持有车辆锁的订单编号。
    private LocalDateTime lockedAt; // 保存订单取得车辆锁的时间。

    public Long getVehicleId() {
        return vehicleId; // 返回唯一的被锁车辆编号。
    }

    public void setVehicleId(Long vehicleId) {
        this.vehicleId = vehicleId; // 写入唯一的被锁车辆编号。
    }

    public Long getOrderId() {
        return orderId; // 返回实际持有车辆锁的订单编号。
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId; // 写入实际持有车辆锁的订单编号。
    }

    public LocalDateTime getLockedAt() {
        return lockedAt; // 返回订单取得车辆锁的时间。
    }

    public void setLockedAt(LocalDateTime lockedAt) {
        this.lockedAt = lockedAt; // 写入订单取得车辆锁的时间。
    }
}
