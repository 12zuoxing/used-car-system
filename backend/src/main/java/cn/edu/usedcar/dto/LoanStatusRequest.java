package cn.edu.usedcar.dto; // 指定工作人员可以提交的处理字段。

import cn.edu.usedcar.model.LoanStatus; // 复用数据库允许的状态枚举。
import jakarta.validation.constraints.NotNull; // 确保选择下一状态。
import jakarta.validation.constraints.PositiveOrZero; // 限制可选版本必须为非负数。
import jakarta.validation.constraints.Size; // 对齐数据库中的办理说明长度。

/** 工作人员更新贷款办理结果时的输入。
 * @author pangzhihao
 */
public record LoanStatusRequest(
        @NotNull(message = "请选择办理状态。") LoanStatus status, // 由服务校验当前状态是否可以进入目标状态。
        @Size(max = 500, message = "办理说明最多 500 个字符。") String resultNote, // 退回补充、完成和关闭需要填写说明。
        @PositiveOrZero(message = "申请版本格式不正确。") Long revision // 页面回传已读版本，避免覆盖后来补充的资料。
) { }
