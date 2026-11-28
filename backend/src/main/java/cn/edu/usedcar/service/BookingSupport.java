package cn.edu.usedcar.service; // 放置代销和预约业务共用的辅助逻辑。

import java.util.Collection; // 允许批量传入车源编号和参与人编号。
import java.util.Map; // 通过编号查找车源和账号名称。
import java.util.function.Function; // 由业务服务定义如何转换当前页的记录。
import java.util.stream.Collectors; // 将批量结果整理成映射。
import cn.edu.usedcar.common.ApiException; // 将业务校验结果转换为统一的 HTTP 错误。
import cn.edu.usedcar.common.PageResult; // 复用系统的分页返回结构。
import cn.edu.usedcar.entity.UserAccountEntity; // 读取账号显示名称。
import cn.edu.usedcar.entity.VehicleEntity; // 读取车源名称。
import cn.edu.usedcar.mapper.AccountMapper; // 批量查询当前页涉及的账号。
import cn.edu.usedcar.mapper.VehicleMapper; // 批量查询当前页涉及的车源。
import com.baomidou.mybatisplus.extension.plugins.pagination.Page; // 使用 MyBatis-Plus 分页对象。
import org.springframework.http.HttpStatus; // 设置与业务情况对应的响应状态码。
import org.springframework.stereotype.Component; // 让两个服务复用由 Spring 注入的实例。

/** 统一分页约束、输入清理和列表名称查询。
 * @author pangzhihao
 */
@Component // 将共享逻辑注册为 Spring 组件。
class BookingSupport {
    private final VehicleMapper vehicles; // 从车源表读取显示名称。
    private final AccountMapper accounts; // 从账号表读取显示名称。

    BookingSupport(VehicleMapper vehicles, AccountMapper accounts) {
        this.vehicles = vehicles; // 保存车辆数据访问接口。
        this.accounts = accounts; // 保存账号数据访问接口。
    }

    static <T> Page<T> page(int page, int size) {
        if (page < 1 || size < 1 || size > 100) { // 拒绝无效页码以及过大的单页请求。
            throw invalid("页码从 1 开始，每页最多 100 条。"); // 向前端解释允许的分页范围。
        }
        return new Page<>(page, size); // 创建数据库分页参数。
    }

    static <T, R> PageResult<R> views(Page<T> page, Function<T, R> convert) {
        return new PageResult<>(page.getRecords().stream().map(convert).toList(), page.getTotal(), // 转换本页的结果并保留总记录数。
                (int) page.getCurrent(), (int) page.getSize()); // 返回请求页码和页大小。
    }

    Names names(Collection<Long> vehicleIds, Collection<Long> accountIds) {
        if (vehicleIds.isEmpty()) return new Names(Map.of(), Map.of()); // 空页直接返回空映射，避免构造空 IN。
        var models = vehicles.selectByIds(vehicleIds).stream().collect(Collectors.toMap( // 一次查询取得当前页的车源名称。
                VehicleEntity::getId, VehicleEntity::getModel)); // 按车源编号索引车型。
        var users = accounts.selectByIds(accountIds).stream().collect(Collectors.toMap( // 一次查询取得当前页的参与人。
                UserAccountEntity::getId, UserAccountEntity::getDisplayName)); // 只把账号显示名称映射到响应。
        return new Names(models, users); // 返回列表转换所需的两个映射。
    }

    static String note(String value) {
        return value == null || value.isBlank() ? null : value.strip(); // 将空白备注归一为空值并去掉两端空格。
    }

    static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_INPUT", message); // 让前端收到统一的参数错误。
    }

    static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message); // 用冲突状态提示列表可能需要刷新。
    }

    record Names(Map<Long, String> vehicles, Map<Long, String> people) { } // 保留展示所需的名称映射。
}
