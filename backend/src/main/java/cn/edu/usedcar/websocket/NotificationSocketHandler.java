package cn.edu.usedcar.websocket; // 管理当前服务器上的通知连接。

import java.io.IOException; // 捕获已经失效连接的 I/O 异常。
import java.util.HashSet; // 收集当前节点在线的接收账号。
import java.util.Set; // 推送查询只处理存在在线连接的账号。
import java.util.concurrent.ConcurrentHashMap; // 安全处理并发握手、断开与计划任务。
import cn.edu.usedcar.model.SessionAccount; // 记录已认证账号和其会话撤销标记。
import cn.edu.usedcar.service.AuthService; // 推送前重新检查账号是否仍然有效。
import jakarta.servlet.http.HttpSession; // 验证连接关联的登录会话尚未失效。
import org.springframework.security.core.Authentication; // 从握手取得安全框架认证的用户。
import org.springframework.stereotype.Component; // 注册为 WebSocket 处理组件。
import org.springframework.web.socket.CloseStatus; // 使用明确的标准关闭原因。
import org.springframework.web.socket.TextMessage; // 仅发送轻量的收件箱刷新信号。
import org.springframework.web.socket.WebSocketSession; // 代表已经升级的浏览器连接。
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator; // 串行化写连接并限制缓冲量。
import org.springframework.web.socket.handler.TextWebSocketHandler; // 处理原生文本 WebSocket。

/** WebSocket 只发送刷新信号，具体通知仍从受保护的 HTTP 接口读取。
 * @author pangzhihao
 */
@Component // 握手配置和推送服务共用这个连接注册表。
public class NotificationSocketHandler extends TextWebSocketHandler {
    public static final String HTTP_SESSION = "notificationHttpSession"; // 内部属性保存握手对应的 Servlet 会话。
    private static final TextMessage SIGNAL = new TextMessage("{\"type\":\"NOTIFICATIONS_CHANGED\"}"); // 消息不携带通知正文、账号或数据库编号。
    private final ConcurrentHashMap<String, Connection> sessions = new ConcurrentHashMap<>(); // 每个标签页独立登记，同时支持多个账号。
    private final AuthService auth; // 推送时核对账号停用和撤销标记。

    public NotificationSocketHandler(AuthService auth) {
        this.auth = auth; // 保存现有认证服务。
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        if (!(session.getPrincipal() instanceof Authentication authentication)
                || !(authentication.getPrincipal() instanceof SessionAccount account)
                || !(session.getAttributes().get(HTTP_SESSION) instanceof HttpSession httpSession)) {
            close(session, CloseStatus.POLICY_VIOLATION); // 验证升级后的连接仍具备有效认证和登录会话。
            return; // 这个连接不进入在线注册表。
        }
        session.setTextMessageSizeLimit(256); // 只支持服务器刷新提示，限制浏览器发来的内容大小。
        var bounded = new ConcurrentWebSocketSessionDecorator(session, 3000, 4096); // 对并发写和缓冲积压设置边界。
        sessions.put(session.getId(), new Connection(bounded, account.profile().id(), account.revocationId(), httpSession)); // 按握手账号登记连接。
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        sessions.remove(session.getId()); // 本通道没有需要客户端提交的业务指令。
        close(session, CloseStatus.POLICY_VIOLATION); // 给非预期的客户端消息关闭连接。
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        sessions.remove(session.getId()); // 传播层出错时清除当前连接。
        close(session, CloseStatus.SERVER_ERROR); // 允许浏览器根据断连策略重建会话。
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId()); // 正常导航、退出和网络断开都释放登记状态。
    }

    public Set<Long> receivers() {
        var result = new HashSet<Long>(); // 收集可用于推送查询的在线账号。
        for (var connection : sessions.values()) {
            if (httpAlive(connection)) result.add(connection.receiverId()); // 仅保留仍关联有效登录会话的连接。
            else disconnect(connection, CloseStatus.POLICY_VIOLATION); // 注销或过期会导致会话自动清理。
        }
        return result; // 离线账号先保留数据库通知，重连后读取。
    }

    public boolean signal(long receiverId) {
        var targets = sessions.values().stream().filter(connection -> connection.receiverId() == receiverId).toList(); // 只向目标账号的标签页发送。
        if (targets.isEmpty()) return false; // 账号不再在线，本次推送可重试。
        var current = auth.activeAccount(receiverId).orElse(null); // 使用最新的账号状态和撤销标记。
        boolean sent = false; // 任一有效标签页收到提示即算完成本轮投递。
        for (var connection : targets) {
            if (current == null || current.revocationId() != connection.revocationId() || !httpAlive(connection)) {
                disconnect(connection, CloseStatus.POLICY_VIOLATION); // 账号停用、会话失效或被撤销时停止推送。
                continue; // 同一账号的其他合法会话仍可处理。
            }
            try {
                connection.socket().sendMessage(SIGNAL); // 把纯刷新信号发送给浏览器。
                sent = true; // 记录至少有一个当前连接成功提交发送。
            } catch (IOException | RuntimeException failure) {
                disconnect(connection, CloseStatus.SERVER_ERROR); // 失败连接由浏览器重连，剩余连接可以继续尝试。
            }
        }
        return sent; // 计划任务据此记录成功或为同一通知安排重试。
    }

    private static boolean httpAlive(Connection connection) {
        try {
            connection.httpSession().getCreationTime(); // Servlet 容器在会话注销或过期后会拒绝此访问。
            return connection.socket().isOpen(); // 同时确认真实套接字仍然打开。
        } catch (IllegalStateException invalidated) {
            return false; // 失效会话关联的连接不能再接收信号。
        }
    }

    private void disconnect(Connection connection, CloseStatus status) {
        sessions.remove(connection.socket().getId(), connection); // 按身份移除当前连接而不触碰其他会话。
        close(connection.socket(), status); // 关闭长连接并向客户端反馈。
    }

    private static void close(WebSocketSession session, CloseStatus status) {
        try {
            if (session.isOpen()) session.close(status); // 连接还有效时请求标准关闭。
        } catch (IOException | RuntimeException failure) {
            // 网络已经不可用时，注册表中的连接已清除。
        }
    }

    private record Connection(WebSocketSession socket, long receiverId, long revocationId, HttpSession httpSession) { } // 关联真实连接与登录状态。
}
