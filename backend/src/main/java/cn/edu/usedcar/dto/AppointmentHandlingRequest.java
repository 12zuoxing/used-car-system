package cn.edu.usedcar.dto; // 定义后台处理预约的请求体。

import cn.edu.usedcar.model.AppointmentStatus; // 复用预约状态枚举，服务再检查允许的流转。
import jakarta.validation.constraints.NotNull; // 经办人须指定处理后的状态。
import jakarta.validation.constraints.Size; // 限制保存给买家的说明长度。

/** 后台看车预约处理参数。
 * @author pangzhihao
 */
public record AppointmentHandlingRequest(
        @NotNull AppointmentStatus status, // 指定确认、退回、取消或完成。
        @Size(max = 500) String note // 接收经办说明，退回和取消必须填写。
) { }
