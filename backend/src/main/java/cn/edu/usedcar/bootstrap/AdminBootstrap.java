package cn.edu.usedcar.bootstrap;

import cn.edu.usedcar.dto.RegisterRequest;
import cn.edu.usedcar.service.AuthService;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 从启动环境读取初始管理员配置。项目不内置管理员口令。
 * @author pangzhihao
 */
@Component
public class AdminBootstrap implements ApplicationRunner {
    private final AuthService auth;
    private final Validator validator;
    private final String username;
    private final String password;
    private final String displayName;

    public AdminBootstrap(AuthService auth, Validator validator,
                          @Value("${app.admin.username:}") String username,
                          @Value("${app.admin.password:}") String password,
                          @Value("${app.admin.display-name:系统管理员}") String displayName) {
        this.auth = auth;
        this.validator = validator;
        this.username = username;
        this.password = password;
        this.displayName = displayName;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (username.isEmpty() && password.isEmpty()) {
            return;
        }
        RegisterRequest firstAdmin = new RegisterRequest(username, password, displayName);
        if (!validator.validate(firstAdmin).isEmpty()) {
            throw new IllegalStateException("初始管理员配置格式不正确，请检查用户名、密码长度和显示名称。");
        }
        auth.bootstrapAdministrator(firstAdmin.username(), firstAdmin.password(), firstAdmin.displayName());
    }
}
