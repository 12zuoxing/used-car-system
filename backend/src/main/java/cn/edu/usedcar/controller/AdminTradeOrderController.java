package cn.edu.usedcar.controller; // 定义交易审核人员的接口。

import cn.edu.usedcar.common.PageResult; // 使用现有分页响应。
import cn.edu.usedcar.config.OpenApiConfig; // 复用 Swagger 会话认证名称。
import cn.edu.usedcar.dto.OrderCompletionRequest; // 接收可选的过户说明。
import cn.edu.usedcar.model.SessionAccount; // 提取执行过户确认的人员。
import cn.edu.usedcar.model.TradeOrderStatus; // 用订单状态筛选后台队列。
import cn.edu.usedcar.service.TradeOrderService; // 由订单业务服务负责事务。
import io.swagger.v3.oas.annotations.Operation; // 描述后台接口用途。
import io.swagger.v3.oas.annotations.Parameter; // 隐藏内部账号参数。
import io.swagger.v3.oas.annotations.security.SecurityRequirement; // 标记后台接口的登录要求。
import io.swagger.v3.oas.annotations.tags.Tag; // 将后台订单接口单独分组。
import jakarta.validation.Valid; // 校验提交的过户说明。
import org.springframework.security.core.annotation.AuthenticationPrincipal; // 从会话获得工作人员。
import org.springframework.web.bind.annotation.GetMapping; // 绑定后台读取接口。
import org.springframework.web.bind.annotation.PathVariable; // 根据路径取得订单号。
import org.springframework.web.bind.annotation.PostMapping; // 绑定成交的写操作。
import org.springframework.web.bind.annotation.RequestBody; // 读取 JSON 过户说明。
import org.springframework.web.bind.annotation.RequestMapping; // 统一后台订单路径。
import org.springframework.web.bind.annotation.RequestParam; // 读取可选筛选和分页字段。
import org.springframework.web.bind.annotation.RestController; // 返回 JSON 业务数据。

/** 工作人员查看订单并登记已支付订单的过户结果。
 * @author pangzhihao
 */
@RestController // 由 Spring MVC 执行业务入口。
@RequestMapping("/api/admin/orders") // 继承管理端 STAFF 或 ADMIN 角色规则。
@Tag(name = "后台交易订单") // 在 Swagger 中展示交易办理接口。
@SecurityRequirement(name = OpenApiConfig.SESSION_COOKIE) // 要求当前操作通过会话认证。
public class AdminTradeOrderController {
    private final TradeOrderService orders; // 订单服务统一处理必要的状态变更。

    public AdminTradeOrderController(TradeOrderService orders) {
        this.orders = orders; // 保存 Spring 注入的业务服务。
    }

    @GetMapping // 分页列出后台正在办理或已完成的交易。
    @Operation(summary = "分页查询后台订单")
    PageResult<TradeOrderService.View> queue(@RequestParam(required = false) TradeOrderStatus status,
                                             @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int pageSize) {
        return orders.queue(status, page, pageSize); // 由服务把状态和页码转换成持久层查询条件。
    }

    @GetMapping("/{id}") // 读取工作人员有权查看的订单详情。
    @Operation(summary = "工作人员查看订单详情")
    TradeOrderService.View detail(@PathVariable long id) {
        return orders.staffDetail(id); // 后台视图不提供个人支付或取消权限。
    }

    @PostMapping("/{id}/complete") // 确认已支付订单的过户结果。
    @Operation(summary = "确认过户并完成已支付订单")
    TradeOrderService.View complete(@PathVariable long id,
            @Valid @RequestBody(required = false) OrderCompletionRequest request, // 过户说明可省略，再由服务整理后保存。
            @AuthenticationPrincipal @Parameter(hidden = true) SessionAccount actor) { // 从会话记录负责确认的人。
        return orders.complete(id, actor.profile().id(), request == null ? null : request.note()); // 原子保存成交、车辆和委托状态。
    }
}
