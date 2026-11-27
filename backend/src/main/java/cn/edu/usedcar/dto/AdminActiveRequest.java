package cn.edu.usedcar.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 账号启停请求。布尔参数必填，避免缺失时被误认为停用。
 * @author pangzhihao
 */
public record AdminActiveRequest(@NotNull(message = "请选择账号状态。") Boolean active) {
}
