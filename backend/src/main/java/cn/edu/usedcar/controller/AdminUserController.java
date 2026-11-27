package cn.edu.usedcar.controller;

import cn.edu.usedcar.common.PageResult;
import cn.edu.usedcar.config.OpenApiConfig;
import cn.edu.usedcar.dto.AccountView;
import cn.edu.usedcar.dto.AdminActiveRequest;
import cn.edu.usedcar.dto.AdminRolesRequest;
import cn.edu.usedcar.model.SessionAccount;
import cn.edu.usedcar.service.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台账号管理接口，访问由安全链限制为管理员。
 * @author pangzhihao
 */
@RestController
@RequestMapping("/api/admin/users")
@Tag(name = "后台账号管理")
@SecurityRequirement(name = OpenApiConfig.SESSION_COOKIE)
public class AdminUserController {
    private final AdminUserService admin;

    public AdminUserController(AdminUserService admin) {
        this.admin = admin;
    }

    @GetMapping
    @Operation(summary = "分页查询账号", description = "仅管理员可访问，页码从 1 开始，每页最多 100 条。")
    PageResult<AccountView> page(@RequestParam(defaultValue = "1") int page,
                                 @RequestParam(defaultValue = "10") int pageSize) {
        return admin.page(page, pageSize).map(AccountView::from);
    }

    @PutMapping("/{id}/roles")
    @Operation(summary = "调整账号角色", description = "变更在已有会话的后续请求中生效。")
    AccountView changeRoles(@PathVariable long id, @Valid @RequestBody AdminRolesRequest request,
                            @AuthenticationPrincipal @Parameter(hidden = true) SessionAccount actor) {
        return AccountView.from(admin.changeRoles(actor, id, request.roles()));
    }

    @PutMapping("/{id}/active")
    @Operation(summary = "启用或停用账号", description = "停用将撤销该账号的旧会话。")
    AccountView changeActive(@PathVariable long id, @Valid @RequestBody AdminActiveRequest request,
                             @AuthenticationPrincipal @Parameter(hidden = true) SessionAccount actor) {
        return AccountView.from(admin.changeActive(actor, id, request.active()));
    }
}
