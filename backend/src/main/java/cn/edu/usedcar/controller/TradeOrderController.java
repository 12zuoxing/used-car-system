package cn.edu.usedcar.controller; // 定义买卖双方通过 HTTP 使用的交易接口。

import java.net.URI; // 创建成功时返回新订单详情地址。
import cn.edu.usedcar.common.PageResult; // 返回统一分页响应。
import cn.edu.usedcar.config.OpenApiConfig; // 引用项目的 Swagger 会话安全配置。
import cn.edu.usedcar.dto.TradeOrderRequest; // 接收下单请求所允许的字段。
import cn.edu.usedcar.model.OrderSide; // 区分买入、卖出和全部个人订单。
import cn.edu.usedcar.model.SessionAccount; // 从已认证会话取得当前用户。
import cn.edu.usedcar.model.TradeOrderStatus; // 为个人订单列表添加状态筛选。
import cn.edu.usedcar.service.TradeOrderService; // 将订单操作交给服务层。
import io.swagger.v3.oas.annotations.Operation; // 描述每个接口的用途。
import io.swagger.v3.oas.annotations.Parameter; // 在文档中标注幂等头并隐藏内部会话参数。
import io.swagger.v3.oas.annotations.responses.ApiResponse; // 说明下单创建、重放和冲突响应。
import io.swagger.v3.oas.annotations.security.SecurityRequirement; // 标记需要登录 Cookie。
import io.swagger.v3.oas.annotations.tags.Tag; // 对交易接口分组。
import jakarta.validation.Valid; // 应用下单请求的字段校验。
import org.springframework.http.ResponseEntity; // 为新订单与幂等重试返回不同状态。
import org.springframework.security.core.annotation.AuthenticationPrincipal; // 从安全上下文获得会话用户。
import org.springframework.web.bind.annotation.GetMapping; // 绑定读取类访问。
import org.springframework.web.bind.annotation.PathVariable; // 读取路径订单编号。
import org.springframework.web.bind.annotation.PostMapping; // 绑定订单状态写入。
import org.springframework.web.bind.annotation.RequestBody; // 解析下单 JSON。
import org.springframework.web.bind.annotation.RequestHeader; // 接收下单请求标识。
import org.springframework.web.bind.annotation.RequestMapping; // 声明订单接口公共路径。
import org.springframework.web.bind.annotation.RequestParam; // 读取列表的分页和筛选。
import org.springframework.web.bind.annotation.RestController; // 注册 JSON 控制器。

/** 个人订单创建、列表、详情、取消和模拟支付的入口。
 * @author pangzhihao
 */
@RestController // 把方法返回的订单视图序列化为 JSON。
@RequestMapping("/api/orders") // 个人交易统一以该路径开头。
@Tag(name = "个人交易订单") // 在 Swagger 中单独展示面向买家的入口。
@SecurityRequirement(name = OpenApiConfig.SESSION_COOKIE) // 请求需要已有登录会话。
public class TradeOrderController {
    private final TradeOrderService orders; // 由服务处理权限、校验和事务。

    public TradeOrderController(TradeOrderService orders) {
        this.orders = orders; // 保存 Spring 注入的订单服务。
    }

    @PostMapping // 接受新下单和同一请求的安全重试。
    @Operation(summary = "下单并锁定指定车辆", responses = {
            @ApiResponse(responseCode = "201", description = "首次成功创建订单"),
            @ApiResponse(responseCode = "200", description = "返回同一请求的原订单"),
            @ApiResponse(responseCode = "409", description = "车辆不可售或请求标识已用于别的车辆")
    })
    ResponseEntity<TradeOrderService.View> create(
            @Parameter(required = true, description = "同一买家的同一次下单及重试使用同一标识，1 至 64 字符")
            @RequestHeader(name = "Idempotency-Key", required = false) String requestKey, // 交给业务层统一验证和匹配原请求。
            @Valid @RequestBody TradeOrderRequest request, // 只允许传入希望购买的车源编号。
            @AuthenticationPrincipal @Parameter(hidden = true) SessionAccount actor) { // 从会话取得下单人。
        var result = orders.create(request.vehicleId(), actor.profile().id(), requestKey); // 把车源和已认证买家传入事务。
        if (!result.fresh()) return ResponseEntity.ok(result.order()); // 幂等重试返回原订单当前状态。
        return ResponseEntity.created(URI.create("/api/orders/" + result.order().id())).body(result.order()); // 创建成功并返回详情地址。
    }

    @GetMapping // 为个人中心提供本人交易历史的分页入口。
    @Operation(summary = "查询本人买入或卖出订单")
    PageResult<TradeOrderService.View> mine(
            @RequestParam(defaultValue = "ALL") OrderSide side, // 支持按交易角色筛选。
            @RequestParam(required = false) TradeOrderStatus status, // 状态筛选可留空。
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int pageSize, // 按项目约定接收页码和页大小。
            @AuthenticationPrincipal @Parameter(hidden = true) SessionAccount actor) { // 列表范围取自会话。
        return orders.mine(actor.profile().id(), side, status, page, pageSize); // 返回属于当前用户的成交或在办记录。
    }

    @GetMapping("/{id}") // 以订单号读取个人订单详情。
    @Operation(summary = "买卖双方查看订单")
    TradeOrderService.View detail(@PathVariable long id, @AuthenticationPrincipal @Parameter(hidden = true) SessionAccount actor) {
        return orders.detail(id, actor.profile().id()); // 只允许属于这笔交易的买家或卖家查看。
    }

    @PostMapping("/{id}/cancel") // 受保护的订单取消入口。
    @Operation(summary = "买卖双方取消待支付订单")
    TradeOrderService.View cancel(@PathVariable long id, @AuthenticationPrincipal @Parameter(hidden = true) SessionAccount actor) {
        return orders.cancel(id, actor.profile().id()); // 核对归属、待支付状态及当前车辆锁。
    }

    @PostMapping("/{id}/pay") // 开展系统内部的模拟支付。
    @Operation(summary = "买家模拟支付订单")
    TradeOrderService.View pay(@PathVariable long id, @AuthenticationPrincipal @Parameter(hidden = true) SessionAccount actor) {
        return orders.pay(id, actor.profile().id()); // 只有订单指定的买家可以更新支付状态。
    }
}
