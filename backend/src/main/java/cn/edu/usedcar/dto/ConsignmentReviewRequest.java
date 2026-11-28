package cn.edu.usedcar.dto; // 定义审核委托时允许提交的字段。

import cn.edu.usedcar.model.ReviewDecision; // 复用项目中的通过或退回选项。
import jakarta.validation.constraints.NotNull; // 要求审核员选择处理结果。
import jakarta.validation.constraints.Size; // 防止审核说明超出字段长度。

/** 工作人员审核委托的输入，操作人从会话中读取。
 * @author pangzhihao
 */
public record ConsignmentReviewRequest(
        @NotNull ReviewDecision decision, // 指定通过审核或退回。
        @Size(max = 500) String note // 保存可选的说明，退回时由服务检查必填。
) { }
