package cn.edu.usedcar.service; // 统一后台统计读取入口。

import cn.edu.usedcar.mapper.DashboardMapper; // 委托持久层获取业务待办。
import org.springframework.stereotype.Service; // 让控制器可以注入工作台服务。
import org.springframework.transaction.annotation.Transactional; // 以只读事务读取统计。

/** 后台工作台的待办统计业务服务。
 * @author pangzhihao
 */
@Service // 启用 Spring 依赖注入。
public class DashboardService {
    private final DashboardMapper dashboard; // 通过 Mapper 获取业务数据。

    public DashboardService(DashboardMapper dashboard) {
        this.dashboard = dashboard; // 保存工作台数据访问接口。
    }

    @Transactional(readOnly = true) // 按一个数据库快照读取当前状态总数。
    public DashboardMapper.Counts snapshot() {
        return dashboard.snapshot(); // 将与队列对应的统计交给后台页面。
    }
}
