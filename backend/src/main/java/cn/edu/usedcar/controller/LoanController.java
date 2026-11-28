package cn.edu.usedcar.controller; // 接收申请人使用的贷款 HTTP 请求。

import java.net.URI; // 新建登记时提供本人详情地址。
import cn.edu.usedcar.common.PageResult; // 返回统一分页结果。
import cn.edu.usedcar.config.OpenApiConfig; // 使用现有的接口文档会话认证。
import cn.edu.usedcar.dto.LoanRequest; // 绑定贷款登记和补充资料表单。
import cn.edu.usedcar.model.LoanStatus; // 提供个人列表的状态筛选。
import cn.edu.usedcar.model.SessionAccount; // 识别登录的申请人。
import cn.edu.usedcar.service.LoanService; // 交给业务层执行贷款操作。
import io.swagger.v3.oas.annotations.Operation; // 为接口文档提供用途说明。
import io.swagger.v3.oas.annotations.Parameter; // 注解路径、版本与内部会话参数。
import io.swagger.v3.oas.annotations.security.SecurityRequirement; // 标记需要认证会话。
import io.swagger.v3.oas.annotations.tags.Tag; // 对面向申请人的接口分组。
import jakarta.validation.Valid; // 对请求体应用字段校验。
import org.springframework.http.ResponseEntity; // 方便区分创建、读取和未登记。
import org.springframework.security.core.annotation.AuthenticationPrincipal; // 从安全上下文取得当前会话。
import org.springframework.web.bind.annotation.GetMapping; // 注册读操作。
import org.springframework.web.bind.annotation.PathVariable; // 绑定路径中的业务编号。
import org.springframework.web.bind.annotation.PostMapping; // 注册首次申请操作。
import org.springframework.web.bind.annotation.PutMapping; // 注册补充资料操作。
import org.springframework.web.bind.annotation.RequestBody; // 解析 JSON 贷款资料。
import org.springframework.web.bind.annotation.RequestMapping; // 定义 REST API 前缀。
import org.springframework.web.bind.annotation.RequestParam; // 读取列表筛选或可选版本。
import org.springframework.web.bind.annotation.RestController; // 以 JSON 形式返回业务结果。

/** 申请人可从订单创建、查看并补充本人的贷款资料。
 * @author pangzhihao
 */
@RestController // 将本类注册给 Spring MVC。
@RequestMapping("/api") // 同时处理订单下的申请入口和独立的贷款入口。
@Tag(name = "个人贷款登记") // 在 Swagger 中聚合个人申请功能。
@SecurityRequirement(name = OpenApiConfig.SESSION_COOKIE) // 使用现有会话验证账号。
public class LoanController {
    private final LoanService loans; // 业务校验由贷款服务负责。

    public LoanController(LoanService loans) {
        this.loans = loans; // 保存容器提供的贷款服务。
    }

    @PostMapping("/orders/{id}/loan") // 按订单提交首次贷款登记。
    @Operation(summary = "订单买家提交贷款登记")
    ResponseEntity<LoanService.Detail> create(@PathVariable long id, @Valid @RequestBody LoanRequest form,
            @AuthenticationPrincipal @Parameter(hidden = true) SessionAccount actor) {
        var result = loans.create(id, actor.profile().id(), form); // 买家身份从会话读取。
        return ResponseEntity.created(URI.create("/api/loans/" + result.id())).body(result); // 提交成功返回详情和读取地址。
    }

    @GetMapping("/orders/{id}/loan") // 从订单进入现有贷款记录或登记表单。
    @Operation(summary = "查询本人订单的贷款登记，尚未登记时返回 204")
    ResponseEntity<LoanService.Detail> byOrder(@PathVariable long id,
            @AuthenticationPrincipal @Parameter(hidden = true) SessionAccount actor) {
        var result = loans.forOrder(id, actor.profile().id()); // 由业务层核对当前账号是否为买家。
        return result == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(result); // 保留未登记与无权访问之间的业务区分。
    }

    @GetMapping("/loans") // 为个人进度页面提供列表数据。
    @Operation(summary = "分页查询本人的贷款登记")
    PageResult<LoanService.Summary> mine(@RequestParam(required = false) LoanStatus status,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int pageSize,
            @AuthenticationPrincipal @Parameter(hidden = true) SessionAccount actor) {
        return loans.mine(actor.profile().id(), status, page, pageSize); // 后端只查询会话申请人名下的登记。
    }

    @GetMapping("/loans/{id}") // 读取本人单条申请和目前的办理说明。
    @Operation(summary = "查看本人的贷款申请详情")
    LoanService.Detail detail(@PathVariable long id, @AuthenticationPrincipal @Parameter(hidden = true) SessionAccount actor) {
        return loans.detail(id, actor.profile().id()); // 按申请归属限定访问。
    }

    @PutMapping("/loans/{id}") // 修改并重新提交已退回补充的申请。
    @Operation(summary = "补充资料并重新提交原登记")
    LoanService.Detail supplement(@PathVariable long id,
            @Parameter(description = "页面当前显示的申请审计版本") @RequestParam(required = false) Long revision,
            @Valid @RequestBody LoanRequest form,
            @AuthenticationPrincipal @Parameter(hidden = true) SessionAccount actor) {
        return loans.supplement(id, actor.profile().id(), revision, form); // 传送当前用户、已读版本和更新后的表单。
    }
}
