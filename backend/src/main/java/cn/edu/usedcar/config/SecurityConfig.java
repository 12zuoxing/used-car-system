package cn.edu.usedcar.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import cn.edu.usedcar.common.ApiError;
import cn.edu.usedcar.security.SessionAccountFilter;
import cn.edu.usedcar.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;

/**
 * 统一配置 REST 接口授权、会话生命周期及 CSRF 保护。
 * @author pangzhihao
 */
@Configuration
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(AuthService auth, PasswordEncoder passwordEncoder) {
        var provider = new DaoAuthenticationProvider(auth::loadUserDetails);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new DelegatingSecurityContextRepository(
                new RequestAttributeSecurityContextRepository(), new HttpSessionSecurityContextRepository());
    }

    @Bean
    HttpSessionCsrfTokenRepository csrfTokenRepository() {
        return new HttpSessionCsrfTokenRepository();
    }

    @Bean
    SessionAuthenticationStrategy sessionAuthenticationStrategy(HttpSessionCsrfTokenRepository repository) {
        // 登录处理器调用标准会话策略，先更换会话编号，再清除登录前的 CSRF 令牌。
        return new CompositeSessionAuthenticationStrategy(List.of(
                new ChangeSessionIdAuthenticationStrategy(), new CsrfAuthenticationStrategy(repository)));
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AuthService auth, ObjectMapper mapper,
                                            SecurityContextRepository contexts,
                                            HttpSessionCsrfTokenRepository csrf) throws Exception {
        return http
                .securityContext(config -> config.securityContextRepository(contexts))
                .csrf(config -> config.csrfTokenRepository(csrf))
                .requestCache(config -> config.disable())
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/error").permitAll()
                        // 文档资源由 springdoc 的启用配置控制，业务鉴权继续使用下方的匹配规则。
                        .requestMatchers(HttpMethod.GET, "/swagger-ui.html", "/swagger-ui/**",
                                "/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                        // 公开车源仅开放列表和数字编号详情，mine 等路径仍需登录。
                        .requestMatchers(HttpMethod.GET, "/api/dictionaries", "/api/vehicles",
                                "/api/vehicles/{id:[0-9]+}").permitAll()
                        .requestMatchers("/api/admin/users", "/api/admin/users/**").hasRole("ADMIN")
                        .requestMatchers("/api/admin/dictionaries", "/api/admin/dictionaries/**").hasRole("ADMIN")
                        .requestMatchers("/api/admin/**").hasAnyRole("ADMIN", "STAFF")
                        .requestMatchers("/api/**", "/ws/**").authenticated()
                        .anyRequest().denyAll())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, error) ->
                                writeError(response, mapper, 401, "UNAUTHENTICATED", "请先登录。"))
                        .accessDeniedHandler((request, response, error) -> {
                            if (error instanceof CsrfException) {
                                writeError(response, mapper, 403, "CSRF_INVALID", "请求令牌已失效，请重新提交。");
                            } else {
                                writeError(response, mapper, 403, "FORBIDDEN", "当前账号无权执行此操作。");
                            }
                        }))
                .logout(logout -> logout.logoutUrl("/api/auth/logout")
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessHandler((request, response, authentication) ->
                                response.setStatus(HttpServletResponse.SC_NO_CONTENT)))
                // 不把过滤器声明成 Servlet Bean，确保它只在安全链内执行一次。
                .addFilterBefore(new SessionAccountFilter(auth), AuthorizationFilter.class)
                .build();
    }

    private void writeError(HttpServletResponse response, ObjectMapper mapper,
                            int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        mapper.writeValue(response.getWriter(), new ApiError(code, message));
    }
}
