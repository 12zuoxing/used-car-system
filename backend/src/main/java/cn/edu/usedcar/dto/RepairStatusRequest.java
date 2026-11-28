package cn.edu.usedcar.dto; // 定义工作人员可以更新的工单字段。

import cn.edu.usedcar.model.RepairStatus; // 目标状态限定为业务枚举。
import jakarta.validation.constraints.NotNull; // 办理时须指定动作。
import jakarta.validation.constraints.Size; // 与办理说明的数据库约束保持一致。

/** 经办人更新工单状态时提交的办理数据。
 * @author pangzhihao
 */
public record RepairStatusRequest(
        @NotNull(message = "请选择处理结果。") RepairStatus status, // 服务层核对真实的起始状态与目标状态。
        @Size(max = 500, message = "办理说明最多 500 个字符。") String resultNote // 完工和取消时应有可读说明。
) { }
