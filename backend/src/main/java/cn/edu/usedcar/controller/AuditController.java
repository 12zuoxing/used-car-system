package cn.edu.usedcar.controller; // 管理端提供操作日志的授权查询。

import cn.edu.usedcar.common.PageResult; // 以项目统一分页结构返回记录。
import cn.edu.usedcar.config.OpenApiConfig; // 复用 Swagger 会话认证名称。
import cn.edu.usedcar.dto.AuditQuery; // 绑定业务类型、操作代码、编号及日期筛选。
import cn.edu.usedcar.model.SessionAccount; // 获取会话实际角色。
import cn.edu.usedcar.service.AuditLogService; // 把过滤及字段权限交给审计服务。
import io.swagger.v3.oas.annotations.Operation; // 描述管理员与工作人员可用的查询。
import io.swagger.v3.oas.annotations.Parameter; // 隐藏会话参数。
import io.swagger.v3.oas.annotations.security.SecurityRequirement; // 标注管理查询需要登录。
import io.swagger.v3.oas.annotations.tags.Tag; // 在 Swagger 中组织日志接口。
import jakarta.validation.Valid; // 校验查询参数格式。
import org.springdoc.core.annotations.ParameterObject; // 在接口文档展开查询条件。
import org.springframework.security.core.annotation.AuthenticationPrincipal; // 获取当前操作人。
import org.springframework.web.bind.annotation.GetMapping; // 注册只读分页入口。
import org.springframework.web.bind.annotation.ModelAttribute; // 将 URL 参数组合成筛选对象。
import org.springframework.web.bind.annotation.RequestMapping; // 指定后台操作日志的路径。
import org.springframework.web.bind.annotation.RestController; // 返回 JSON 日志视图。

/** 按角色查询审计记录和可公开字段。
 * @author pangzhihao
 */
@RestController // 将控制器注册到管理端。
@RequestMapping("/api/admin/audit-logs") // 继承工作人员与管理员的安全策略。
@Tag(name = "后台操作日志") // 单独展示审计查询。
@SecurityRequirement(name = OpenApiConfig.SESSION_COOKIE) // 访问需要管理会话。
public class AuditController {
    private final AuditLogService audit; // 复用此前负责日志入库的审计服务。

    public AuditController(AuditLogService audit) {
        this.audit = audit; // 保存 Spring 注入的服务。
    }

    @GetMapping // 按时间、对象或业务动作查询日志。
    @Operation(summary = "按职责分页查询操作日志")
    PageResult<AuditLogService.View> list(@Valid @ModelAttribute @ParameterObject AuditQuery query,
            @AuthenticationPrincipal @Parameter(hidden = true) SessionAccount actor) {
        return audit.list(actor, query); // 以会话信息确定数据范围和说明列可见性。
    }
}
