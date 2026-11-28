package cn.edu.usedcar.config; // 配置实时通知的认证握手与后台调度。

import java.util.Map; // 在握手和升级处理器之间传递内部会话引用。
import cn.edu.usedcar.model.SessionAccount; // 确认请求使用有效的应用账号登录。
import cn.edu.usedcar.websocket.NotificationSocketHandler; // 将实时路径绑定到通知处理器。
import org.springframework.context.annotation.Configuration; // 激活配置类。
import org.springframework.http.server.ServerHttpRequest; // 读取 WebSocket 握手的请求数据。
import org.springframework.http.server.ServerHttpResponse; // 遵循握手拦截器签名。
import org.springframework.http.server.ServletServerHttpRequest; // 读取现有 Servlet 会话。
import org.springframework.scheduling.annotation.EnableScheduling; // 启用已提交通知的后台投递计划。
import org.springframework.security.core.Authentication; // 从安全过滤器处理后的请求读取身份。
import org.springframework.web.socket.WebSocketHandler; // 标识当前握手使用的处理器。
import org.springframework.web.socket.config.annotation.EnableWebSocket; // 启用原生 WebSocket 支持。
import org.springframework.web.socket.config.annotation.WebSocketConfigurer; // 注册实时通知路由。
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry; // 添加路径及握手拦截器。
import org.springframework.web.socket.server.HandshakeInterceptor; // 对会话和账号执行握手校验。

/** 与 HTTP 通知读取共用会话身份的实时通道。
 * @author pangzhihao
 */
@Configuration // 让 Spring 应用 WebSocket 与调度配置。
@EnableWebSocket // 注册浏览器使用的实时连接。
@EnableScheduling // 轮询已经提交的通知并处理失败重试。
public class WebSocketConfig implements WebSocketConfigurer {
    private final NotificationSocketHandler handler; // 管理本节点上的登录长连接。

    public WebSocketConfig(NotificationSocketHandler handler) {
        this.handler = handler; // 注入已有通知处理器。
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/notifications").addInterceptors(new SessionHandshake()); // 保留 Spring 默认的同源限制，重复确认登录来源。
    }

    private static class SessionHandshake implements HandshakeInterceptor {
        @Override
        public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler,
                                       Map<String, Object> attributes) {
            if (!(request instanceof ServletServerHttpRequest servlet)
                    || !(request.getPrincipal() instanceof Authentication authentication)
                    || !authentication.isAuthenticated()
                    || !(authentication.getPrincipal() instanceof SessionAccount)) return false; // 匿名或不是有效账号的握手不会升级。
            var session = servlet.getServletRequest().getSession(false); // 只接受已存在的登录会话。
            if (session == null) return false; // 握手不会为未登录浏览器创建新会话。
            attributes.put(NotificationSocketHandler.HTTP_SESSION, session); // 只传递以后验证连接生效所需的内部引用。
            return true; // 会话认证成立，可以建立通知连接。
        }

        @Override
        public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler,
                                   Exception failure) {
            // 握手成功后的连接登记由 NotificationSocketHandler 负责。
        }
    }
}
