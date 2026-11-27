package cn.edu.usedcar.dto;

import java.util.Set;

import cn.edu.usedcar.model.Role;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * 管理员要为指定账号保留的角色集合。
 * @author pangzhihao
 */
public record AdminRolesRequest(
        @NotEmpty(message = "至少需要选择一种角色。")
        Set<@NotNull(message = "角色不能为空。") Role> roles) {
}
