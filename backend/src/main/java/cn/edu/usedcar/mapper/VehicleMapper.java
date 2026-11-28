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
}
