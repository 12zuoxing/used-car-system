package cn.edu.usedcar.mapper; // 集中管理送修工单的数据库访问。

import cn.edu.usedcar.entity.RepairOrderEntity; // 映射 repair_order 数据表。
import com.baomidou.mybatisplus.core.mapper.BaseMapper; // 复用 MyBatis-Plus 的 CRUD 和分页。
import org.apache.ibatis.annotations.Param; // 为定制查询声明参数名称。
import org.apache.ibatis.annotations.Select; // 绑定锁车之前的外键查询。

/** 工单持久层。先读不可变的车辆编号，再由服务锁车并重读工单。
 * @author pangzhihao
 */
public interface RepairMapper extends BaseMapper<RepairOrderEntity> {
    @Select("SELECT vehicle_id FROM repair_order WHERE id = #{id}") // 此时只读取外键，不缓存未锁定的工单状态。
    Long vehicleId(@Param("id") long id); // 返回工单当前关联车辆的编号。
}
