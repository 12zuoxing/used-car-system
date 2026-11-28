package cn.edu.usedcar.dto; // 定义工作人员登记成交时填写的内容。

import jakarta.validation.constraints.Size; // 与订单处理说明字段的长度一致。

/** 过户确认时允许录入适合交易双方查看的说明。
 * @author pangzhihao
 */
public record OrderCompletionRequest(
        @Size(max = 500, message = "过户说明最多 500 个字符。") String note // 保存可选的过户处理说明。
) { }
