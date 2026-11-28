package cn.edu.usedcar.mapper;

import java.time.LocalDateTime; // 接收公共附件查询所需的业务时间。
import cn.edu.usedcar.entity.AttachmentEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

/**
 * 受控下载的可见范围与附件读取合并在一条查询中。
 * @author pangzhihao
 */
public interface AttachmentMapper extends BaseMapper<AttachmentEntity> {
    AttachmentEntity selectReadable(@Param("id") long id, @Param("viewerId") Long viewerId,
                                    @Param("manager") boolean manager,
                                    @Param("businessNow") LocalDateTime businessNow); // 在同一次查询内判断附件和代销车的可见性。
}
