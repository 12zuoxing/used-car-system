package cn.edu.usedcar.controller; // 接收工作人员使用的贷款经办请求。

import cn.edu.usedcar.common.PageResult; // 带上总量和页码返回队列摘要。
import cn.edu.usedcar.config.OpenApiConfig; // 复用 Swagger 会话配置。
import cn.edu.usedcar.dto.LoanStatusRequest; // 接收目标状态、结果说明及访问版本。
import cn.edu.usedcar.model.LoanStatus; // 定义后台筛选接口的可选状态。
import cn.edu.usedcar.model.SessionAccount; // 从会话取得处理人身份。
import cn.edu.usedcar.service.LoanService; // 由服务处理后台操作。
import io.swagger.v3.oas.annotations.Operation; // 在接口文档标记使用方式。
import io.swagger.v3.oas.annotations.Parameter; // 隐藏后台注入的会话参数。
import io.swagger.v3.oas.annotations.security.SecurityRequirement; // 在 Swagger 标记受认证保护。
import io.swagger.v3.oas.annotations.tags.Tag; // 将经办接口单独归组。
import jakarta.validation.Valid; // 验证经办条件字段的格式。
import org.springframework.security.core.annotation.AuthenticationPrincipal; // 从登录状态获取当前工作人员。
import org.springframework.web.bind.annotation.GetMapping; // 注册列表与详情读取。
import org.springframework.web.bind.annotation.PathVariable; // 接收 URL 中的贷款编号。
import org.springframework.web.bind.annotation.PostMapping; // 注册修改办理结果的入口。
import org.springframework.web.bind.annotation.RequestBody; // 在请求体内接收办理数据。
import org.springframework.web.bind.annotation.RequestMapping; // 统一后台接口前缀。
import org.springframework.web.bind.annotation.RequestParam; // 接收分页与筛选参数。
import org.springframework.web.bind.annotation.RestController; // 注册 REST 控制器。

/** 贷款队列、资料复核与办理结果更新。
 * @author pangzhihao
 */
@RestController // 将处理方法映射为 JSON 接口。
@RequestMapping("/api/admin/loans") // 继承管理端的工作人员和管理员角色校验。
@Tag(name = "后台贷款办理") // 将经办接口与个人申请分开展示。
@SecurityRequirement(name = OpenApiConfig.SESSION_COOKIE) // 在文档中标明需要认证。
public class AdminLoanController {
    private final LoanService loans; // 复用同一套状态管理。

    public AdminLoanController(LoanService loans) {
        this.loans = loans; // 注入贷款业务服务。
    }

    @GetMapping // 查询工作人员可见的申请队列。
    @Operation(summary = "分页查询贷款办理队列")
    PageResult<LoanService.Summary> queue(@RequestParam(required = false) LoanStatus status,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int pageSize) {
        return loans.queue(status, page, pageSize); // 根据状态和分页字段读后台列表。
    }

    @GetMapping("/{id}") // 打开经办详情以读取最新材料和版本。
    @Operation(summary = "工作人员查看贷款登记详情")
    LoanService.Detail detail(@PathVariable long id) {
        return loans.staffDetail(id); // 包含经过权限检查后的申请及办理说明。
    }

    @PostMapping("/{id}/status") // 退回补充、开始办理或登记结果。
    @Operation(summary = "更新贷款办理进度")
    LoanService.Detail handle(@PathVariable long id, @Valid @RequestBody LoanStatusRequest request,
            @AuthenticationPrincipal @Parameter(hidden = true) SessionAccount actor) {
        return loans.handle(id, actor.profile().id(), request); // 审计使用实际会话经办人。
    }
}
