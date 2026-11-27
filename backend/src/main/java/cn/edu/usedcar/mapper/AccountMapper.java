package cn.edu.usedcar.mapper;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import cn.edu.usedcar.common.PageResult;
import cn.edu.usedcar.entity.UserAccountEntity;
import cn.edu.usedcar.model.AccountProfile;
import cn.edu.usedcar.model.Role;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 账号持久层。账号写入与分页使用 MyBatis-Plus，角色关联和锁查询使用定制 SQL。
 * @author pangzhihao
 */
public interface AccountMapper extends BaseMapper<UserAccountEntity> {
    List<AccountRoleRow> selectIdentityByUsername(@Param("username") String username);

    List<AccountRoleRow> selectIdentityById(@Param("id") long id);

    List<RoleRow> selectRolesByUserIds(@Param("ids") List<Long> ids);

    @Insert("INSERT INTO user_role (user_id, role_code) VALUES (#{id}, #{role})")
    void addRole(@Param("id") long id, @Param("role") Role role);

    @Delete("DELETE FROM user_role WHERE user_id = #{id}")
    void removeRoles(@Param("id") long id);

    /** 初始化与权限修改在同一角色记录上加锁，串行检查最后一位管理员。 */
    @Select("SELECT code FROM app_role WHERE code = 'ADMIN' FOR UPDATE")
    String lockAdminRole();

    long countAdministrators(@Param("activeOnly") boolean activeOnly);

    default Optional<Account> findByUsername(String username) {
        return toAccount(selectIdentityByUsername(username));
    }

    default Optional<Account> findById(long id) {
        return toAccount(selectIdentityById(id));
    }

    default long create(String username, String passwordHash, String displayName) {
        var entity = new UserAccountEntity();
        entity.setUsername(username);
        entity.setPasswordHash(passwordHash);
        entity.setDisplayName(displayName);
        insert(entity);
        return Objects.requireNonNull(entity.getId(), "数据库未返回账号编号");
    }

    default void updateActive(long id, boolean active) {
        update(null, Wrappers.lambdaUpdate(UserAccountEntity.class)
                .eq(UserAccountEntity::getId, id)
                .set(UserAccountEntity::getActive, active));
    }

    /**
     * 先分页账号，再一次查询该页的全部角色。这样一个多角色账号仍只占一个分页位置，
     * 列表 SQL 也无需读取密码散列。
     */
    default PageResult<AccountProfile> page(int page, int pageSize) {
        var result = selectPage(new Page<>(page, pageSize), Wrappers.lambdaQuery(UserAccountEntity.class)
                .select(UserAccountEntity::getId, UserAccountEntity::getUsername,
                        UserAccountEntity::getDisplayName, UserAccountEntity::getActive)
                .orderByAsc(UserAccountEntity::getId));
        if (result.getRecords().isEmpty()) {
            return new PageResult<>(List.of(), result.getTotal(), page, pageSize);
        }

        var ids = result.getRecords().stream().map(UserAccountEntity::getId).toList();
        Map<Long, List<Role>> rolesByUser = selectRolesByUserIds(ids).stream().collect(Collectors.groupingBy(
                RoleRow::userId, Collectors.mapping(RoleRow::role, Collectors.toList())));
        var items = result.getRecords().stream()
                .map(user -> new AccountProfile(user.getId(), user.getUsername(), user.getDisplayName(),
                        user.getActive(), rolesByUser.getOrDefault(user.getId(), List.of())))
                .toList();
        return new PageResult<>(items, result.getTotal(), page, pageSize);
    }

    /** 同一条查询取得账号、角色和撤销标记，避免组合来自不同快照的数据。 */
    private static Optional<Account> toAccount(List<AccountRoleRow> rows) {
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        var first = rows.get(0);
        var roles = rows.stream().map(AccountRoleRow::role).filter(Objects::nonNull).toList();
        var profile = new AccountProfile(first.id(), first.username(), first.displayName(), first.active(), roles);
        return Optional.of(new Account(profile, first.passwordHash(), first.revocationId()));
    }

    record Account(AccountProfile profile, String passwordHash, long revocationId) {
    }

    /** 一条关联查询结果对应一个账号角色。没有角色时 role 为空。 */
    record AccountRoleRow(long id, String username, String displayName, boolean active,
                          String passwordHash, long revocationId, Role role) {
    }

    record RoleRow(long userId, Role role) {
    }
}
