package cn.edu.usedcar.controller; // 提供管理工作台的待办统计。

import cn.edu.usedcar.config.OpenApiConfig; // 复用 Swagger 会话认证设置。
import cn.edu.usedcar.mapper.DashboardMapper; // 使用服务返回的结构化计数记录。
import cn.edu.usedcar.service.DashboardService; // 由统计服务获取队列快照。
import io.swagger.v3.oas.annotations.Operation; // 描述工作台接口。
import io.swagger.v3.oas.annotations.security.SecurityRequirement; // 标注后台认证要求。
import io.swagger.v3.oas.annotations.tags.Tag; // 在 Swagger 中聚合工作台操作。
import org.springframework.web.bind.annotation.GetMapping; // 绑定当前待办查询。
import org.springframework.web.bind.annotation.RequestMapping; // 指定管理端统计路径。
import org.springframework.web.bind.annotation.RestController; // 返回计数 JSON。

/** 后台各业务队列的工作台快照。
 * @author pangzhihao
 */
@RestController // 由 Spring MVC 注册只读控制器。
@RequestMapping("/api/admin/dashboard") // 沿用管理端的角色校验。
@Tag(name = "管理工作台") // 在接口文档单独说明统计。
@SecurityRequirement(name = OpenApiConfig.SESSION_COOKIE) // 访问要求认证会话。
public class DashboardController {
    private final DashboardService dashboard; // 读取业务表生成后台待办数字。

    public DashboardController(DashboardService dashboard) {
        this.dashboard = dashboard; // 保存依赖注入提供的统计服务。
    }

    @GetMapping // 返回当前各队列对应的计数。
    @Operation(summary = "查询车源、订单、贷款和工单待办")
    DashboardMapper.Counts snapshot() {
        return dashboard.snapshot(); // 控制器负责转发，不复制统计条件。
    }
}
