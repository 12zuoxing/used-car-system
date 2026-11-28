package cn.edu.usedcar.controller; // 为登录用户提供个人收件箱入口。

import cn.edu.usedcar.config.OpenApiConfig; // 引用项目现有的会话安全配置。
import cn.edu.usedcar.model.SessionAccount; // 使用当前登录账号锁定接收范围。
import cn.edu.usedcar.service.NotificationService; // 把查询和标记交给业务服务。
import io.swagger.v3.oas.annotations.Operation; // 在文档中说明请求行为。
import io.swagger.v3.oas.annotations.Parameter; // 隐藏安全框架注入的会话内部数据。
import io.swagger.v3.oas.annotations.security.SecurityRequirement; // 标明需要登录会话。
import io.swagger.v3.oas.annotations.tags.Tag; // 为个人通知组织独立分组。
import org.springframework.http.HttpStatus; // 使用无内容状态响应已读操作。
import org.springframework.security.core.annotation.AuthenticationPrincipal; // 从认证上下文取得会话账号。
import org.springframework.web.bind.annotation.GetMapping; // 注册通知列表查询。
import org.springframework.web.bind.annotation.PathVariable; // 绑定通知编号。
import org.springframework.web.bind.annotation.PostMapping; // 注册标记已读的写请求。
import org.springframework.web.bind.annotation.RequestMapping; // 设置个人通知公共前缀。
import org.springframework.web.bind.annotation.RequestParam; // 绑定分页和未读筛选。
import org.springframework.web.bind.annotation.ResponseStatus; // 为重复标记已读提供相同状态。
import org.springframework.web.bind.annotation.RestController; // 以 JSON 返回收件箱数据。

/** 仅向会话中的接收人返回通知。
 * @author pangzhihao
 */
@RestController // 让 Spring MVC 绑定通知接口。
@RequestMapping("/api/notifications") // 统一个人通知路径。
@Tag(name = "站内通知") // 在 Swagger 中聚合读取和已读状态接口。
@SecurityRequirement(name = OpenApiConfig.SESSION_COOKIE) // 使用登录 Cookie 验证账号。
public class NotificationController {
    private final NotificationService notifications; // 统一校验记录归属与请求页码。

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications; // 保存由 Spring 注入的服务。
    }

    @GetMapping // 查询本人通知和未读汇总。
    @Operation(summary = "分页查询个人通知及未读数量")
    NotificationService.Inbox list(@RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int pageSize,
            @AuthenticationPrincipal @Parameter(hidden = true) SessionAccount actor) {
        return notifications.list(actor.profile().id(), unreadOnly, page, pageSize); // 带上当前账号读取个人收件箱。
    }

    @PostMapping("/{id}/read") // 只有所属接收账号能标记该通知。
    @ResponseStatus(HttpStatus.NO_CONTENT) // 首次和重复操作使用相同结果。
    @Operation(summary = "将本人的指定通知标记为已读")
    void markRead(@PathVariable long id, @AuthenticationPrincipal @Parameter(hidden = true) SessionAccount actor) {
        notifications.markRead(id, actor.profile().id()); // 由数据库保存首个有效已读时刻。
    }
}
