package cn.edu.usedcar.mapper; // 负责车辆当前锁记录的读写。

import cn.edu.usedcar.entity.VehicleLockEntity; // 使用车源编号作为锁记录主键。
import com.baomidou.mybatisplus.core.mapper.BaseMapper; // 复用创建和查询当前锁的方法。
import org.apache.ibatis.annotations.Delete; // 声明按复合条件释放锁的 SQL。
import org.apache.ibatis.annotations.Param; // 显式传入车辆和订单两个编号。

/** 车辆当前锁 Mapper，通过车源号与订单号一起验证锁归属。
 * @author pangzhihao
 */
public interface VehicleLockMapper extends BaseMapper<VehicleLockEntity> {
    // 仅删除当前车辆确实由指定订单持有的锁。
    @Delete("DELETE FROM vehicle_lock WHERE vehicle_id = #{vehicleId} AND order_id = #{orderId}")
    int release(@Param("vehicleId") long vehicleId, @Param("orderId") long orderId); // 返回删除条数供事务判断是否真正释放。
}
