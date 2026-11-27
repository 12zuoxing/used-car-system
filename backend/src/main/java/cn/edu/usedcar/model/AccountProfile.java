package cn.edu.usedcar.model;

import java.io.Serializable;
import java.util.List;

/**
 * 业务层及会话使用的账号资料快照。
 * 类型只包含账号标识、状态和角色，适合在业务层传递。
 * @author pangzhihao
 */
public record AccountProfile(long id, String username, String displayName, boolean active, List<Role> roles)
        implements Serializable {
    public AccountProfile {
        roles = List.copyOf(roles);
    }
}
