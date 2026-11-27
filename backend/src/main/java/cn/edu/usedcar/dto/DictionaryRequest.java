package cn.edu.usedcar.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 字典保存请求。停用是状态变更，保留已关联的车辆记录。
 * @author pangzhihao
 */
public record DictionaryRequest(
        @NotBlank(message = "请输入字典名称。") @Size(max = 40, message = "字典名称最多 40 个字符。") String name,
        @NotNull(message = "请选择启用状态。") Boolean active) {
}
