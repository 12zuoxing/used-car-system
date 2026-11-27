package cn.edu.usedcar.controller;

import cn.edu.usedcar.config.OpenApiConfig;
import cn.edu.usedcar.dto.AccountView;
import cn.edu.usedcar.dto.LoginRequest;
import cn.edu.usedcar.dto.RegisterRequest;
import cn.edu.usedcar.model.SessionAccount;
import cn.edu.usedcar.security.SessionLoginHandler;
import cn.edu.usedcar.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 接收账号认证请求，校验输入并转换接口响应。认证会话由安全组件维护。
 * @author pangzhihao
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "账号认证")
public class AuthController {
    private final AuthService auth;
    private final SessionLoginHandler loginHandler;

    public AuthController(AuthService auth, SessionLoginHandler loginHandler) {
        this.auth = auth;
        this.loginHandler = loginHandler;
    }

    /** 读取令牌时初始化会话。前端将令牌放入写请求的指定头部。 */
    @GetMapping("/csrf")
    @Operation(summary = "获取 CSRF 令牌", description = "写请求前获取令牌，登录后再次获取。")
    CsrfView csrf(@Parameter(hidden = true) CsrfToken token) {
        return new CsrfView(token.getHeaderName(), token.getToken());
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "注册普通用户", responses = {
            @ApiResponse(responseCode = "201", description = "注册成功", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "409", description = "用户名已存在")
    })
    AccountView register(@Valid @RequestBody RegisterRequest request) {
        return AccountView.from(auth.register(request.username(), request.password(), request.displayName()));
    }

    @PostMapping("/login")
    @Operation(summary = "登录", description = "登录成功后浏览器获得会话 Cookie。")
    AccountView login(@RequestBody LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        return AccountView.from(loginHandler.login(body.username(), body.password(), request, response));
    }

    @GetMapping("/me")
    @Operation(summary = "查看当前登录账号", security = @SecurityRequirement(name = OpenApiConfig.SESSION_COOKIE))
    AccountView me(@AuthenticationPrincipal @Parameter(hidden = true) SessionAccount account) {
        return AccountView.from(account.profile());
    }

    record CsrfView(String headerName, String token) {
    }
}
