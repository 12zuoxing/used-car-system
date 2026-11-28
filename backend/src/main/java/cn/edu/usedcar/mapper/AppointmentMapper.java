package cn.edu.usedcar.mapper; // 集中看车预约的持久化操作。

import cn.edu.usedcar.entity.AppointmentEntity; // 指定预约表对应的实体。
import com.baomidou.mybatisplus.core.mapper.BaseMapper; // 使用 MyBatis-Plus 的增改查和分页能力。
import org.apache.ibatis.annotations.Param; // 命名需要绑定的预约编号。
import org.apache.ibatis.annotations.Select; // 声明锁车源前的外键查询。

/** 看车预约表的单表访问接口。
 * @author pangzhihao
 */
public interface AppointmentMapper extends BaseMapper<AppointmentEntity> {
    @Select("SELECT vehicle_id FROM appointment WHERE id = #{id}") // 先取车辆编号，取得车源行锁后再读取完整预约。
    Long vehicleId(@Param("id") long id); // 按预约主键查找锁定目标。
}
