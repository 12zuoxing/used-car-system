package cn.edu.usedcar.mapper;

import cn.edu.usedcar.entity.AttachmentEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 受控下载的可见范围与附件读取合并在一条查询中。
 * @author pangzhihao
 */
public interface AttachmentMapper extends BaseMapper<AttachmentEntity> {
    @Select("""
            SELECT a.*
            FROM attachment a
            JOIN vehicle v ON v.id = a.vehicle_id
            LEFT JOIN vehicle_report r ON r.id = a.report_id AND r.vehicle_id = a.vehicle_id
            WHERE a.id = #{id}
              AND (#{manager} = TRUE OR v.owner_id = #{viewerId,jdbcType=BIGINT}
                   OR (v.status = 'LISTED' AND (a.kind = 'PHOTO' OR r.status = 'APPROVED')))
            """)
    AttachmentEntity selectReadable(@Param("id") long id, @Param("viewerId") Long viewerId,
                                    @Param("manager") boolean manager);
}
