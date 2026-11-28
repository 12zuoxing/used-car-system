package cn.edu.usedcar.dto; // 定义提交代销委托时允许传入的字段。

import java.math.BigDecimal; // 使用十进制保存金额，避免浮点舍入。
import java.time.LocalDateTime; // 接收不附带偏移量的北京时间。
import io.swagger.v3.oas.annotations.media.Schema; // 为接口文档标注业务时区。
import jakarta.validation.constraints.Digits; // 对齐金额字段的整数位和小数位。
import jakarta.validation.constraints.NotNull; // 拒绝缺少必填值的请求。
import jakarta.validation.constraints.PositiveOrZero; // 允许零费用并拒绝负数。
import jakarta.validation.constraints.Size; // 限制代销约定文字的长度。

/** 委托提交参数，发布人由登录会话确定。
 * @author pangzhihao
 */
public record ConsignmentRequest(
        @Schema(description = "开始时间（北京时间）") @NotNull LocalDateTime startsAt, // 指定委托最早生效时刻。
        @Schema(description = "截止时间（北京时间，截止时刻不包含）") @NotNull LocalDateTime expiresAt, // 指定自动失效的时刻。
        @NotNull @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal fee, // 接收最多两位小数的人民币费用。
        @Size(max = 600) String terms // 接收可选的委托约定。
) { }
