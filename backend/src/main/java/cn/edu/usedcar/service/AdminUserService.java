package cn.edu.usedcar.service;

import java.util.Set;

import cn.edu.usedcar.common.ApiException;
import cn.edu.usedcar.common.PageResult;
import cn.edu.usedcar.mapper.AccountMapper;
import cn.edu.usedcar.model.AccountProfile;
import cn.edu.usedcar.model.Role;
import cn.edu.usedcar.model.SessionAccount;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 管理员分页查询账号、停用与启用账号、调整角色。
 * @author pangzhihao
 */
@Service
public class AdminUserService {
    private final AccountMapper accounts;
    private final AuditLogService audit;

    public AdminUserService(AccountMapper accounts, AuditLogService audit) {
        this.accounts = accounts;
        this.audit = audit;
    }

    // 分页、总数和批量角色查询在同一读快照中完成。
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<AccountProfile> page(int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "页码从 1 开始，每页最多 100 条。");
        }
        return accounts.page(page, pageSize);
    }

    @Transactional
    public AccountProfile changeRoles(SessionAccount actor, long targetId, Set<Role> roles) {
        beginAdminChange(actor);
        AccountProfile target = findUser(targetId);
        if (Set.copyOf(target.roles()).equals(roles)) {
            return target;
        }
        if (target.active() && target.roles().contains(Role.ADMIN) && !roles.contains(Role.ADMIN)) {
            requireAnotherAdministrator();
        }

        accounts.removeRoles(targetId);
        roles.stream().sorted().forEach(role -> accounts.addRole(targetId, role));
        audit.record(actor.profile().id(), "CHANGE_USER_ROLES", "USER", targetId,
                "角色：" + target.roles() + " → " + roles.stream().sorted().toList());
        return findUser(targetId);
    }

    @Transactional
    public AccountProfile changeActive(SessionAccount actor, long targetId, boolean active) {
        beginAdminChange(actor);
        AccountProfile target = findUser(targetId);
        if (target.active() == active) {
            return target;
        }
        if (!active && target.roles().contains(Role.ADMIN)) {
            requireAnotherAdministrator();
        }

        accounts.updateActive(targetId, active);
        // 停用审计同时作为撤销标记，必须跟账号状态在同一个事务提交。
        audit.record(actor.profile().id(), active ? "ENABLE_USER" : "DISABLE_USER", "USER", targetId,
                active ? "启用账号" : "停用账号");
        return findUser(targetId);
    }

    private void beginAdminChange(SessionAccount actor) {
        // 获取互斥锁后再次检查权限及会话撤销标记，避免旧的并发请求继续修改账号。
        accounts.lockAdminRole();
        boolean allowed = accounts.findById(actor.profile().id())
                .filter(account -> account.revocationId() == actor.revocationId())
                .map(AccountMapper.Account::profile)
                .filter(user -> user.active() && user.roles().contains(Role.ADMIN)).isPresent();
        if (!allowed) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "当前账号无权管理用户。");
        }
    }

    private void requireAnotherAdministrator() {
        if (accounts.countAdministrators(true) <= 1) {
            throw new ApiException(HttpStatus.CONFLICT, "LAST_ADMIN", "系统至少需要保留一个启用的管理员。");
        }
    }

    private AccountProfile findUser(long id) {
        return accounts.findById(id).map(AccountMapper.Account::profile)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "账号不存在。"));
    }
}
