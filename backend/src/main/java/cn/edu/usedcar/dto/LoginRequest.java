package cn.edu.usedcar.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * JSON 登录请求。用户名与密码由认证流程校验，并使用统一的登录失败响应。
 * @author pangzhihao
 */
public record LoginRequest(String username, @Schema(format = "password", accessMode = Schema.AccessMode.WRITE_ONLY)
                           String password) {
}
