package cn.edu.usedcar.dto; // 只接收买家可以填写的预约信息。

import java.time.LocalDateTime; // 接收页面填写的北京时间。
import io.swagger.v3.oas.annotations.media.Schema; // 在 Swagger 中注明时间的解释方式。
import jakarta.validation.constraints.NotNull; // 到访时间必须填写。
import jakarta.validation.constraints.Size; // 与预约备注字段长度匹配。

/** 看车预约表单，车辆从路径读取，买家从会话读取。
 * @author pangzhihao
 */
public record AppointmentRequest(
        @Schema(description = "预计到访时间（北京时间）") @NotNull LocalDateTime visitAt, // 保存买家选择的到访时间。
        @Size(max = 400) String note // 允许买家填写看车需求。
) { }
