package cn.edu.usedcar.mapper;

import java.time.LocalDateTime; // 接收服务层提供的统一业务时间。
import cn.edu.usedcar.entity.VehicleEntity;
import cn.edu.usedcar.model.VehicleFilter;
import cn.edu.usedcar.model.VehicleRecord;
import cn.edu.usedcar.model.VehicleStatus;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update; // 通过条件更新将订单事务与车辆状态绑定。

/**
 * 车辆数据访问接口。单表写入使用 BaseMapper，组合筛选使用联表投影。
 * @author pangzhihao
 */
public interface VehicleMapper extends BaseMapper<VehicleEntity> {
    Page<VehicleRecord> selectVehiclePage(Page<VehicleRecord> page, @Param("criteria") VehicleFilter criteria,
                                          @Param("status") VehicleStatus status, @Param("ownerId") Long ownerId,
                                          @Param("managed") boolean managed,
                                          @Param("businessNow") LocalDateTime businessNow); // 列表查询按同一时刻判断代销有效期。

    VehicleRecord selectView(@Param("id") long id, @Param("status") VehicleStatus status,
                             @Param("ownerId") Long ownerId, @Param("managed") boolean managed,
                             @Param("businessNow") LocalDateTime businessNow); // 详情和审核状态采用相同的有效期规则。

    boolean isPublic(@Param("id") long id, @Param("businessNow") LocalDateTime businessNow); // 判断公开车源与关联媒体的读取资格。

    /** 所有车源状态变更先锁定同一车源行，再核对归属和状态。 */
    @Select("SELECT * FROM vehicle WHERE id = #{id} FOR UPDATE")
    VehicleEntity selectForUpdate(@Param("id") long id);

    // 只更新仍在预期状态的指定车辆，防止旧交易覆盖新状态。
    @Update("UPDATE vehicle SET status = #{next}, listed_at = #{listedAt,jdbcType=TIMESTAMP} WHERE id = #{id} AND status = #{expected}")
    int transitionOrderStatus(@Param("id") long id, // 使用订单绑定的车源编号。
                              @Param("expected") VehicleStatus expected, // 校验开始操作时预期的业务状态。
                              @Param("next") VehicleStatus next, // 保存锁定、恢复在售、下架或已售状态。
                              @Param("listedAt") LocalDateTime listedAt); // 车辆无资格重新展示时清除上架时间。
}
