package cn.edu.usedcar.dto; // 定义买家可填写的贷款登记数据。

import java.math.BigDecimal; // 使用十进制数精确接收金额。
import jakarta.validation.constraints.DecimalMin; // 约束申请金额至少为一分钱。
import jakarta.validation.constraints.Digits; // 对齐数据库的有效位数和精度。
import jakarta.validation.constraints.Max; // 限制最大贷款期数。
import jakarta.validation.constraints.Min; // 限制最小贷款期数。
import jakarta.validation.constraints.NotNull; // 确保金额和期限存在。
import jakarta.validation.constraints.Size; // 限制备注文本长度。

/** 初次登记和补充资料共用相同的字段与格式校验。
 * @author pangzhihao
 */
public record LoanRequest(
        @NotNull(message = "请填写申请金额。") // 缺少金额时返回可读提示。
        @DecimalMin(value = "0.01", message = "申请金额必须大于 0 元。") // 允许精确到分的正金额。
        @Digits(integer = 10, fraction = 2, message = "申请金额最多保留两位小数。") BigDecimal amount, // 与 DECIMAL(12, 2) 对齐。
        @NotNull(message = "请填写贷款期限。") // 期数必须由申请人明确提供。
        @Min(value = 1, message = "贷款期限应为 1 至 84 期。") // 至少选择一期。
        @Max(value = 84, message = "贷款期限应为 1 至 84 期。") Integer months, // 至多选择 84 期。
        @Size(max = 500, message = "申请说明最多 500 个字符。") String note // 允许填写可选的人工登记说明。
) { }
