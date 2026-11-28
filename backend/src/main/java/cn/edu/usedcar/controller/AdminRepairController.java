package cn.edu.usedcar.controller; // 管理端查询和办理售前售后工单。

import cn.edu.usedcar.common.PageResult; // 返回后台队列的分页信息。
import cn.edu.usedcar.config.OpenApiConfig; // 引用统一的 Swagger 会话配置。
import cn.edu.usedcar.dto.RepairStatusRequest; // 接收经办人员填写的状态与说明。
import cn.edu.usedcar.model.RepairStage; // 按售前或售后筛选记录。
import cn.edu.usedcar.model.RepairStatus; // 按办理进度筛选记录。
import cn.edu.usedcar.model.SessionAccount; // 从安全上下文取得经办人员。
import cn.edu.usedcar.service.RepairService; // 提供工单业务和事务操作。
import io.swagger.v3.oas.annotations.Operation; // 描述经办步骤。
import io.swagger.v3.oas.annotations.Parameter; // 隐藏安全框架使用的内部参数。
import io.swagger.v3.oas.annotations.security.SecurityRequirement; // 标记认证要求。
import io.swagger.v3.oas.annotations.tags.Tag; // 用独立分组显示后台工单接口。
import jakarta.validation.Valid; // 验证目标状态和办理说明的格式。
import org.springframework.security.core.annotation.AuthenticationPrincipal; // 从会话中读取身份。
import org.springframework.web.bind.annotation.GetMapping; // 绑定后台查询操作。
import org.springframework.web.bind.annotation.PathVariable; // 读取路径工单编号。
import org.springframework.web.bind.annotation.PostMapping; // 绑定状态处理操作。
import org.springframework.web.bind.annotation.RequestBody; // 接收状态更新 JSON。
import org.springframework.web.bind.annotation.RequestMapping; // 配置管理端路径。
import org.springframework.web.bind.annotation.RequestParam; // 接收分页字段。
import org.springframework.web.bind.annotation.RestController; // 返回管理端 REST JSON。

/** 工作人员使用的整备与送修队列和状态办理入口。
 * @author pangzhihao
 */
@RestController // 让 Spring MVC 处理业务请求。
@RequestMapping("/api/admin/repairs") // 继承后台 STAFF、ADMIN 角色权限。
@Tag(name = "后台整备与送修") // 在 Swagger 中聚合后台功能。
@SecurityRequirement(name = OpenApiConfig.SESSION_COOKIE) // 经办接口要求已有登录会话。
public class AdminRepairController {
    private final RepairService repairs; // 状态条件和交易归属统一由服务层处理。

    public AdminRepairController(RepairService repairs) {
        this.repairs = repairs; // 保存依赖注入提供的服务。
    }

    @GetMapping // 获取工单办理队列。
    @Operation(summary = "按业务阶段和状态查询工单队列")
    PageResult<RepairService.Summary> queue(@RequestParam(required = false) RepairStage stage,
            @RequestParam(required = false) RepairStatus status,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int pageSize) {
        return repairs.queue(stage, status, page, pageSize); // 分页展示选中状态及阶段下的工单摘要。
    }

    @GetMapping("/{id}") // 受理前读取最新工单说明及到店安排。
    @Operation(summary = "工作人员查看工单详情")
    RepairService.Detail detail(@PathVariable long id) {
        return repairs.staffDetail(id); // 工作人员通过管理角色读取授权资料。
    }

    @PostMapping("/{id}/status") // 保存受理、完工或取消结果。
    @Operation(summary = "工作人员更新工单办理状态")
    RepairService.Detail handle(@PathVariable long id, @Valid @RequestBody RepairStatusRequest request,
            @AuthenticationPrincipal @Parameter(hidden = true) SessionAccount actor) {
        return repairs.handle(id, actor.profile().id(), request); // 使用实际经办身份进行审计和状态变更。
    }
}
