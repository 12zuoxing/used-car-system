package cn.edu.usedcar.controller; // Web 层负责参数绑定和 JSON 响应。

import cn.edu.usedcar.common.PageResult; // 使用通用分页结果。
import cn.edu.usedcar.config.OpenApiConfig; // 引用共享会话认证文档配置。
import cn.edu.usedcar.dto.ConsignmentRequest; // 接收该接口允许的请求数据。
import cn.edu.usedcar.model.SessionAccount; // 从会话读取操作人。
import cn.edu.usedcar.service.ConsignmentService; // 将业务操作委托给服务层。
import io.swagger.v3.oas.annotations.Operation; // 描述具体接口用途。
import io.swagger.v3.oas.annotations.Parameter; // 隐藏由安全框架注入的参数。
import io.swagger.v3.oas.annotations.security.SecurityRequirement; // 将会话鉴权标注到 Swagger。
import io.swagger.v3.oas.annotations.tags.Tag; // 按职责组织 Swagger 接口。
import jakarta.validation.Valid; // 在进入业务层前验证表单。
import org.springframework.http.HttpStatus; // 使用创建成功状态。
import org.springframework.security.core.annotation.AuthenticationPrincipal; // 从 Spring Security 取得当前会话用户。
import org.springframework.web.bind.annotation.GetMapping; // 将查询方法绑定到 GET 请求。
import org.springframework.web.bind.annotation.PathVariable; // 从 URL 路径取得业务编号。
import org.springframework.web.bind.annotation.PostMapping; // 将更新方法绑定到 POST 请求。
import org.springframework.web.bind.annotation.RequestBody; // 将 JSON 请求体转换为对象。
import org.springframework.web.bind.annotation.RequestMapping; // 设置 API 路径前缀。
import org.springframework.web.bind.annotation.RequestParam; // 接收过滤和分页参数。
import org.springframework.web.bind.annotation.ResponseStatus; // 设置正常响应状态。
import org.springframework.web.bind.annotation.RestController; // 将方法结果序列化成 JSON。

/** 代销委托接口，访问权限由现有会话与角色配置管理。
 * @author pangzhihao
 */
@RestController // 将本类注册为 REST 控制器。
@RequestMapping("/api") // 所有业务接口挂在统一的 API 路径下。
@Tag(name = "代销委托") // 在 Swagger 中单独组织本组接口。
@SecurityRequirement(name = OpenApiConfig.SESSION_COOKIE) // 在接口文档提示需要登录会话。
public class ConsignmentController {
    private final ConsignmentService consignments; // 将所有业务规则留在服务层。

    public ConsignmentController(ConsignmentService consignments) {
        this.consignments = consignments; // 保存 Spring 注入的业务服务。
    }

    @PostMapping("/vehicles/{id}/consignments") // 绑定此操作对应的 HTTP 路径。
    @ResponseStatus(HttpStatus.CREATED) // 用 201 告知委托已创建。
    @Operation(summary = "提交代销委托") // 在 Swagger 中显示接口用途。
    ConsignmentService.View create(@PathVariable long id, @Valid @RequestBody ConsignmentRequest request,
              @AuthenticationPrincipal @Parameter(hidden = true) SessionAccount actor) {
        return consignments.create(id, actor.profile().id(), request); // 将车源、登录车主和委托表单传给业务层。
    }

    @GetMapping("/vehicles/{id}/consignments") // 绑定此操作对应的 HTTP 路径。
    @Operation(summary = "查询本人车源的委托历史") // 在 Swagger 中显示接口用途。
    PageResult<ConsignmentService.View> mine(@PathVariable long id, @RequestParam(defaultValue = "1") int page,
              @RequestParam(defaultValue = "10") int pageSize,
              @AuthenticationPrincipal @Parameter(hidden = true) SessionAccount actor) {
        return consignments.mine(id, actor.profile().id(), page, pageSize); // 由服务按车源和登录车主限定记录范围。
    }

    @PostMapping("/consignments/{id}/withdraw") // 绑定此操作对应的 HTTP 路径。
    @Operation(summary = "车主撤回代销委托") // 在 Swagger 中显示接口用途。
    ConsignmentService.View withdraw(@PathVariable long id, @AuthenticationPrincipal @Parameter(hidden = true) SessionAccount actor) {
        return consignments.withdraw(id, actor.profile().id()); // 由服务检查车主、委托状态及在办订单。
    }
}
