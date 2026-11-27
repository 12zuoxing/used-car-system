package cn.edu.usedcar.security;

import cn.edu.usedcar.model.AccountProfile;
import cn.edu.usedcar.model.SessionAccount;
import cn.edu.usedcar.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

/**
 * 将账号认证与 Servlet 会话衔接。控制器只接收登录结果，业务服务可独立于 HTTP 会话。
 * @author pangzhihao
 */
@Component
public class SessionLoginHandler {
    private final AuthService auth;
    private final AuthenticationManager authenticationManager;
    private final SessionAuthenticationStrategy sessions;
    private final SecurityContextRepository contexts;

    public SessionLoginHandler(AuthService auth, AuthenticationManager authenticationManager,
                               SessionAuthenticationStrategy sessions, SecurityContextRepository contexts) {
        this.auth = auth;
        this.authenticationManager = authenticationManager;
        this.sessions = sessions;
        this.contexts = contexts;
    }

    public AccountProfile login(String username, String password,
                                HttpServletRequest request, HttpServletResponse response) {
        auth.validateLogin(username, password);
        var verified = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(username, password));
        SessionAccount current = auth.authenticatedAccount(verified.getName());

        // JSON 登录需要显式执行会话策略，再把认证后的安全上下文保存到会话。
        var authentication = toAuthentication(current);
        sessions.onAuthentication(authentication, request, response);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        return current.profile();
    }

    /** 会话只保存身份快照与授权信息，认证完成后不保存密码或散列。 */
    static UsernamePasswordAuthenticationToken toAuthentication(SessionAccount account) {
        var authorities = account.profile().roles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                .toList();
        return UsernamePasswordAuthenticationToken.authenticated(account, null, authorities);
    }
}
