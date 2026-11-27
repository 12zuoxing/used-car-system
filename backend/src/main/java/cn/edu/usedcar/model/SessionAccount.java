package cn.edu.usedcar.model;

import java.io.Serializable;
import java.security.Principal;

/**
 * 会话内部身份。撤销标记与账号信息同时从数据库读取，不暴露在 HTTP 响应中。
 * @author pangzhihao
 */
public record SessionAccount(AccountProfile profile, long revocationId) implements Principal, Serializable {
    @Override
    public String getName() {
        return profile.username();
    }
}
