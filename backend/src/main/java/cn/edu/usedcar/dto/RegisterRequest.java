package cn.edu.usedcar.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 公开注册只接受用户可填写字段。角色由服务端固定分配。
 * @author pangzhihao
 */
public record RegisterRequest(
        @NotBlank(message = "请输入用户名。")
        @Pattern(regexp = "[a-zA-Z0-9_]{3,32}", message = "用户名须为 3–32 位字母、数字或下划线。")
        String username,
        @NotBlank(message = "请输入密码。")
        @Size(min = 8, max = 72, message = "密码至少需要 8 个字符。")
        @Schema(format = "password", accessMode = Schema.AccessMode.WRITE_ONLY)
        String password,
        @NotBlank(message = "请输入显示名称。")
        @Size(max = 40, message = "显示名称最多 40 个字符。")
        String displayName) {
}
