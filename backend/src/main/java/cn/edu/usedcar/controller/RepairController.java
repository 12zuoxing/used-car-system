package cn.edu.usedcar.controller; // 接收普通用户的送修与整备请求。

import java.net.URI; // 创建成功时指向可查询的工单。
import cn.edu.usedcar.common.PageResult; // 复用项目的分页结构。
import cn.edu.usedcar.config.OpenApiConfig; // 引用统一的 Swagger 会话配置。
import cn.edu.usedcar.dto.RepairRequest; // 绑定带有业务校验的提交参数。
import cn.edu.usedcar.model.RepairStage; // 提供列表的阶段筛选。
import cn.edu.usedcar.model.RepairStatus; // 提供列表的状态筛选。
import cn.edu.usedcar.model.SessionAccount; // 从会话取得工单申请人。
import cn.edu.usedcar.service.RepairService; // 将事务与流程校验交给服务层。
import io.swagger.v3.oas.annotations.Operation; // 描述各接口的业务含义。
import io.swagger.v3.oas.annotations.Parameter; // 隐藏由安全框架注入的账号参数。
import io.swagger.v3.oas.annotations.security.SecurityRequirement; // 在接口文档标注认证要求。
import io.swagger.v3.oas.annotations.tags.Tag; // 将个人服务工单聚合到一个操作组。
import jakarta.validation.Valid; // 启用提交字段的 Bean Validation。
import org.springframework.http.ResponseEntity; // 返回创建状态与资源位置。
import org.springframework.security.core.annotation.AuthenticationPrincipal; // 注入当前登录身份。
import org.springframework.web.bind.annotation.GetMapping; // 注册个人工单读取。
import org.springframework.web.bind.annotation.PathVariable; // 绑定指定工单的编号。
import org.springframework.web.bind.annotation.PostMapping; // 注册创建和主动取消操作。
import org.springframework.web.bind.annotation.RequestBody; // 接收 JSON 提交内容。
import org.springframework.web.bind.annotation.RequestMapping; // 设置控制器的公共路径。
import org.springframework.web.bind.annotation.RequestParam; // 读取筛选和分页参数。
import org.springframework.web.bind.annotation.RestController; // 返回 REST JSON 数据。

/** 当前用户的工单创建、查看和取消入口。
 * @author pangzhihao
 */
@RestController // 将本类注册为 Spring MVC 控制器。
@RequestMapping("/api/repairs") // 个人售前和售后使用同一接口资源。
@Tag(name = "个人整备与送修") // 在 Swagger 文档中组织属于申请人的操作。
@SecurityRequirement(name = OpenApiConfig.SESSION_COOKIE) // 个人工单必须登录访问。
public class RepairController {
    private final RepairService repairs; // 由业务服务核对车源归属、订单状态和操作资格。

    public RepairController(RepairService repairs) {
        this.repairs = repairs; // 保存注入的服务实例。
    }

    @PostMapping // 在当前用户下创建新的整备或送修工单。
    @Operation(summary = "提交售前整备或售后送修申请")
    ResponseEntity<RepairService.Detail> create(@Valid @RequestBody RepairRequest form,
            @AuthenticationPrincipal @Parameter(hidden = true) SessionAccount actor) {
        var result = repairs.create(actor.profile().id(), form); // 申请人从会话读取，避免前端指定他人。
        return ResponseEntity.created(URI.create("/api/repairs/" + result.id())).body(result); // 返回 201 和详情地址。
    }

    @GetMapping // 分页读取买家或发布人本人发起的工单。
    @Operation(summary = "分页查询本人的工单")
    PageResult<RepairService.Summary> mine(@RequestParam(required = false) RepairStage stage,
            @RequestParam(required = false) RepairStatus status,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int pageSize,
            @AuthenticationPrincipal @Parameter(hidden = true) SessionAccount actor) {
        return repairs.mine(actor.profile().id(), stage, status, page, pageSize); // 服务层按申请人、阶段和状态限定记录。
    }

    @GetMapping("/{id}") // 根据工单编号读取授权详情。
    @Operation(summary = "查看本人提交的工单详情")
    RepairService.Detail detail(@PathVariable long id, @AuthenticationPrincipal @Parameter(hidden = true) SessionAccount actor) {
        return repairs.detail(id, actor.profile().id()); // 返回本人的描述、到店时间和处理结果。
    }

    @PostMapping("/{id}/cancel") // 个人取消尚未受理的既有记录。
    @Operation(summary = "取消本人尚待受理的工单")
    RepairService.Detail cancel(@PathVariable long id, @AuthenticationPrincipal @Parameter(hidden = true) SessionAccount actor) {
        return repairs.cancel(id, actor.profile().id()); // 校验申请人身份与工单当前状态。
    }
}
