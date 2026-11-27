package cn.edu.usedcar.service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import cn.edu.usedcar.common.ApiException;
import cn.edu.usedcar.mapper.AccountMapper;
import cn.edu.usedcar.model.AccountProfile;
import cn.edu.usedcar.model.Role;
import cn.edu.usedcar.model.SessionAccount;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 负责注册、首次管理员初始化和有效身份读取。
 * @author pangzhihao
 */
@Service
public class AuthService {
    private static final Pattern USERNAME_PATTERN = Pattern.compile("[a-zA-Z0-9_]{3,32}");
    private static final int MAX_BCRYPT_BYTES = 72;

    private final AccountMapper accounts;
    private final PasswordEncoder encoder;
    private final AuditLogService audit;

    public AuthService(AccountMapper accounts, PasswordEncoder encoder, AuditLogService audit) {
        this.accounts = accounts;
        this.encoder = encoder;
        this.audit = audit;
    }

    /** 账号、初始角色必须同时写入。用户名冲突交由数据库唯一键裁决并转换为 409。 */
    @Transactional
    public AccountProfile register(String username, String password, String displayName) {
        return createAccount(username, password, displayName, Role.USER);
    }

    /**
     * 仅在指定初始凭据且数据库还没有管理员时创建。重复启动不会覆盖已有账号或密码。
     * 与管理员角色变更使用同一行锁，以便多实例同时首次启动时只创建一次。
     */
    @Transactional
    public void bootstrapAdministrator(String username, String password, String displayName) {
        accounts.lockAdminRole();
        if (accounts.countAdministrators(false) > 0) {
            return;
        }
        if (accounts.findByUsername(normalizeUsername(username)).isPresent()) {
            throw new IllegalStateException("初始管理员用户名已被使用，请配置另一个用户名。");
        }
        AccountProfile admin = createAccount(username, password, displayName, Role.ADMIN);
        audit.record(null, "CREATE_INITIAL_ADMIN", "USER", admin.id(), "初始化管理员账号");
    }

    /** 使用单次查询获取最新状态，供每次已登录请求重新载入角色。 */
    public Optional<SessionAccount> activeAccount(long id) {
        return accounts.findById(id).filter(account -> account.profile().active()).map(AuthService::sessionAccount);
    }

    public SessionAccount authenticatedAccount(String username) {
        return accounts.findByUsername(username).filter(account -> account.profile().active())
                .map(AuthService::sessionAccount)
                .orElseThrow(() -> new BadCredentialsException("账号已失效"));
    }

    /** Spring Security 仅在校验密码时读取散列，应用会话中使用不含散列的账号信息。 */
    public UserDetails loadUserDetails(String username) {
        var account = accounts.findByUsername(normalizeUsername(username))
                .orElseThrow(() -> new UsernameNotFoundException("账号不存在"));
        AccountProfile user = account.profile();
        return User.withUsername(user.username()).password(account.passwordHash())
                .roles(user.roles().stream().map(Role::name).toArray(String[]::new))
                .disabled(!user.active())
                .build();
    }

    public void validateLogin(String username, String password) {
        if (username == null || !USERNAME_PATTERN.matcher(username).matches()
                || password == null || password.isBlank()
                || password.getBytes(StandardCharsets.UTF_8).length > MAX_BCRYPT_BYTES) {
            throw new BadCredentialsException("账号或密码不正确");
        }
    }

    private AccountProfile createAccount(String username, String password, String displayName, Role role) {
        String normalizedUsername = normalizeUsername(username);
        String normalizedDisplayName = displayName.strip();
        // BCrypt 按字节计算上限。UTF-8 中文与表单字符长度不是同一个单位。
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BCRYPT_BYTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "密码不能超过 72 个 UTF-8 字节。");
        }

        long id;
        try {
            id = accounts.create(normalizedUsername, encoder.encode(password), normalizedDisplayName);
        } catch (DuplicateKeyException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "USERNAME_TAKEN", "该用户名已被使用。");
        }
        accounts.addRole(id, role);
        return new AccountProfile(id, normalizedUsername, normalizedDisplayName, true, List.of(role));
    }

    private static SessionAccount sessionAccount(AccountMapper.Account account) {
        return new SessionAccount(account.profile(), account.revocationId());
    }

    private static String normalizeUsername(String username) {
        return username.toLowerCase(Locale.ROOT);
    }
}
