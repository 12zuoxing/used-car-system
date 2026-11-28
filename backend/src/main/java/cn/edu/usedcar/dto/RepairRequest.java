package cn.edu.usedcar.dto; // 定义用户提交送修或整备时的输入范围。

import java.time.LocalDateTime; // 接收预计送修的本地业务时间。
import cn.edu.usedcar.model.RepairStage; // 校验所选业务阶段是已知的枚举值。
import io.swagger.v3.oas.annotations.media.Schema; // 在接口文档标明业务场景和时区。
import jakarta.validation.constraints.NotBlank; // 拒绝空白的工单问题描述。
import jakarta.validation.constraints.NotNull; // 对申请的关键字段做非空验证。
import jakarta.validation.constraints.Positive; // 车辆和订单编号须为正数。
import jakarta.validation.constraints.Size; // 限制处理描述在数据库允许范围内。

/** 申请人只能填写送修目标和内容，不能指定经办人与办理状态。
 * @author pangzhihao
 */
public record RepairRequest(
        @NotNull(message = "请选择工单类型。") RepairStage stage, // 业务层按售前或售后验证归属。
        @NotNull(message = "请选择车辆。") @Positive(message = "车辆编号格式不正确。") Long vehicleId, // 指定申请对应机动车辆。
        @Schema(description = "售后必填，售前留空") @Positive(message = "订单编号格式不正确。") Long tradeOrderId, // 仅售后工单必须关联成交订单。
        @Schema(description = "预计到店时间，采用北京时间") @NotNull(message = "请填写预计到店时间。") LocalDateTime visitAt, // 保存客户选择的到店时刻。
        @NotBlank(message = "请填写整备或送修说明。") @Size(max = 800, message = "说明最多 800 个字符。") String description // 接收具体检查项目或故障情况。
) { }
