package cn.edu.usedcar.mapper; // 将委托表的 SQL 访问集中在持久层。

import java.time.LocalDateTime; // 用同一个业务时刻比较开始和截止时间。
import cn.edu.usedcar.entity.ConsignmentEntity; // 关联委托表实体。
import com.baomidou.mybatisplus.core.mapper.BaseMapper; // 提供单表插入、更新和分页查询。
import org.apache.ibatis.annotations.Param; // 显式命名 SQL 参数。
import org.apache.ibatis.annotations.Select; // 声明定制查询。

/** 委托记录及与它关联的交易查询。
 * @author pangzhihao
 */
public interface ConsignmentMapper extends BaseMapper<ConsignmentEntity> {
    // 锁车源后查询当前有效记录，同时校验委托属于当前发布人。
    @Select("""
            SELECT id FROM consignment
            WHERE vehicle_id = #{vehicleId} AND owner_id = #{ownerId} AND status = 'APPROVED'
              AND starts_at <= #{now} AND expires_at > #{now}
            ORDER BY id DESC LIMIT 1
            """)
    Long activeId(@Param("vehicleId") long vehicleId, // 限定同一车源。
                  @Param("ownerId") long ownerId, // 限定当前发布人。
                  @Param("now") LocalDateTime now); // 按左闭右开区间判断有效性。

    @Select("SELECT vehicle_id FROM consignment WHERE id = #{id}") // 单独读取车辆外键，锁定车辆后才读取完整委托。
    Long vehicleId(@Param("id") long id); // 返回当前委托对应的车源编号。

    // 撤回前检查有无在办订单，同时覆盖车辆与交易状态暂未同步的情况。
    @Select("SELECT COUNT(*) FROM trade_order WHERE vehicle_id = #{vehicleId} AND status IN ('LOCKED', 'PAID')")
    long countInProgressOrders(@Param("vehicleId") long vehicleId); // 仅计算锁定和已支付的订单。
}
