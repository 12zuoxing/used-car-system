package cn.edu.usedcar.dto;

import cn.edu.usedcar.model.ReviewDecision;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 审核请求携带本轮提交版本，防止老页面审核后来重新提交的资料。
 * @author pangzhihao
 */
public record VehicleReviewRequest(@NotNull(message = "缺少车源提交版本。") @PositiveOrZero Long revision,
                                   @NotNull(message = "请选择审核结果。") ReviewDecision decision,
                                   @Size(max = 500, message = "审核说明最多 500 个字符。") String note) {
}
