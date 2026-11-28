package cn.edu.usedcar.controller; // Web 层负责参数绑定和 JSON 响应。

import cn.edu.usedcar.common.PageResult; // 使用通用分页结果。
import cn.edu.usedcar.config.OpenApiConfig; // 引用共享会话认证文档配置。
import cn.edu.usedcar.dto.AppointmentRequest; // 接收该接口允许的请求数据。
import cn.edu.usedcar.model.AppointmentStatus; // 接收定义好的状态筛选枚举。
import cn.edu.usedcar.model.SessionAccount; // 从会话读取操作人。
import cn.edu.usedcar.service.AppointmentService; // 将业务操作委托给服务层。
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

/** 看车预约接口，访问权限由现有会话与角色配置管理。
 * @author pangzhihao
 */
@RestController // 将本类注册为 REST 控制器。
@RequestMapping("/api") // 所有业务接口挂在统一的 API 路径下。
@Tag(name = "看车预约") // 在 Swagger 中单独组织本组接口。
@SecurityRequirement(name = OpenApiConfig.SESSION_COOKIE) // 在接口文档提示需要登录会话。
public class AppointmentController {
    private final AppointmentService appointments; // 将所有业务规则留在服务层。

    public AppointmentController(AppointmentService appointments) {
        this.appointments = appointments; // 保存 Spring 注入的业务服务。
    }

    @PostMapping("/vehicles/{id}/appointments") // 绑定此操作对应的 HTTP 路径。
    @ResponseStatus(HttpStatus.CREATED) // 用 201 告知买家申请已提交。
    @Operation(summary = "申请看车预约") // 在 Swagger 中显示接口用途。
    AppointmentService.View create(@PathVariable long id, @Valid @RequestBody AppointmentRequest request,
              @AuthenticationPrincipal @Parameter(hidden = true) SessionAccount actor) {
        return appointments.create(id, actor.profile().id(), request); // 由业务层校验车源、买家身份和到访时间。
    }

    @GetMapping("/appointments") // 绑定此操作对应的 HTTP 路径。
    @Operation(summary = "分页查询本人看车预约") // 在 Swagger 中显示接口用途。
    PageResult<AppointmentService.View> mine(@RequestParam(required = false) AppointmentStatus status,
              @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int pageSize,
              @AuthenticationPrincipal @Parameter(hidden = true) SessionAccount actor) {
        return appointments.mine(actor.profile().id(), status, page, pageSize); // 将查询范围限定为当前会话中的买家。
    }

    @PostMapping("/appointments/{id}/cancel") // 绑定此操作对应的 HTTP 路径。
    @Operation(summary = "买家取消预约") // 在 Swagger 中显示接口用途。
    AppointmentService.View cancel(@PathVariable long id, @AuthenticationPrincipal @Parameter(hidden = true) SessionAccount actor) {
        return appointments.cancel(id, actor.profile().id()); // 由业务层核对归属并执行取消。
    }
}
