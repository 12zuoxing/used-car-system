package cn.edu.usedcar.dto; // 定义后台取消已模拟支付订单的请求内容。

import jakarta.validation.constraints.NotBlank; // 要求工作人员填写实际取消原因。
import jakarta.validation.constraints.Size; // 将原因长度限制在数据库字段的容量内。

/** 工作人员取消已模拟支付的订单时，填写供交易双方查看的原因。
 * @author pangzhihao
 */
public record OrderCancellationRequest(
        @NotBlank(message = "请填写取消原因。") // 拒绝缺失或只包含空白的原因。
        @Size(max = 500, message = "取消原因最多 500 个字符。") // 对应订单处理说明列的最大长度。
        String note // 接收工作人员在取消对话框中输入的原因。
) { }
