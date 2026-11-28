package cn.edu.usedcar.mapper; // 集中管理订单表的数据库访问。

import cn.edu.usedcar.entity.TradeOrderEntity; // 映射订单记录。
import com.baomidou.mybatisplus.core.mapper.BaseMapper; // 复用 MyBatis-Plus 的分页、插入和更新。
import org.apache.ibatis.annotations.Param; // 显式命名 SQL 参数。
import org.apache.ibatis.annotations.Select; // 声明主键和幂等标识查询。

/** 交易订单 Mapper，负责订单查询和持久化。
 * @author pangzhihao
 */
public interface TradeOrderMapper extends BaseMapper<TradeOrderEntity> {
    // 复用唯一键读取同一个买家的原请求。
    @Select("SELECT * FROM trade_order WHERE buyer_id = #{buyerId} AND request_key = #{requestKey}")
    TradeOrderEntity findByRequest(@Param("buyerId") long buyerId, @Param("requestKey") String requestKey); // 按买家与标识定位同一次下单。

    // 只读取不会变化的车辆外键，待锁住车源后再读取完整订单状态。
    @Select("SELECT vehicle_id FROM trade_order WHERE id = #{id}")
    Long vehicleId(@Param("id") long id); // 按订单编号定位需要锁定的车辆。
}
