package cn.edu.usedcar.mapper;

import cn.edu.usedcar.entity.VehicleReportEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 报告数据访问。新增附件和审核在锁定同一报告时互斥。
 * @author pangzhihao
 */
public interface ReportMapper extends BaseMapper<VehicleReportEntity> {
    @Select("SELECT * FROM vehicle_report WHERE id = #{id} FOR UPDATE")
    VehicleReportEntity selectForUpdate(@Param("id") long id);
}
