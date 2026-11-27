package cn.edu.usedcar.dto;

import java.util.List;

import cn.edu.usedcar.model.AccountProfile;
import cn.edu.usedcar.model.Role;

/**
 * 对前端公开的账号响应。控制器只拷贝允许公开的字段。
 * @author pangzhihao
 */
public record AccountView(long id, String username, String displayName, boolean active, List<Role> roles) {
    public static AccountView from(AccountProfile profile) {
        return new AccountView(profile.id(), profile.username(), profile.displayName(), profile.active(), profile.roles());
    }
}
