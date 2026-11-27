package cn.edu.usedcar.security;

import java.io.IOException;

import cn.edu.usedcar.model.SessionAccount;
import cn.edu.usedcar.service.AuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 在授权判断前加载账号的当前角色。已有会话也会感知权限调整与停用操作。
 * @author pangzhihao
 */
public class SessionAccountFilter extends OncePerRequestFilter {
    private final AuthService auth;

    public SessionAccountFilter(AuthService auth) {
        this.auth = auth;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof SessionAccount stored) {
            var current = auth.activeAccount(stored.profile().id());
            // 即使账号已经重新启用，停用前的会话也不应重新获得访问。
            if (current.isPresent() && current.get().revocationId() == stored.revocationId()) {
                // 使用新的请求级上下文，避免在同一会话的并行请求之间改写共享的角色快照。
                var context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(SessionLoginHandler.toAuthentication(current.get()));
                SecurityContextHolder.setContext(context);
            } else {
                var session = request.getSession(false);
                if (session != null) {
                    try {
                        session.invalidate();
                    } catch (IllegalStateException alreadyInvalidated) {
                        // 另一个并行请求可能已经清除了这个会话。
                    }
                }
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
