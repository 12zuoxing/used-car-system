package cn.edu.usedcar.service;

import java.time.LocalDate; // 校验日期筛选的数据库有效区间。
import java.time.LocalDateTime; // 返回记录入库时间。
import java.util.Map; // 通过账号编号快速查显示名。
import java.util.Set; // 定义工作人员能查询的业务类别。
import java.util.Objects; // 去除系统日志没有执行人的空编号。
import java.util.stream.Collectors; // 批量收集日志执行者名称。
import cn.edu.usedcar.common.PageResult; // 使用统一分页结构。
import cn.edu.usedcar.dto.AuditQuery; // 接收后台按类型、动作、对象和日期的筛选。
import cn.edu.usedcar.entity.AuditLogEntity; // 审计表读取模型。
import cn.edu.usedcar.entity.UserAccountEntity; // 仅提取执行者显示名。
import cn.edu.usedcar.mapper.AccountMapper; // 一次查询当前页记录涉及的账号。
import cn.edu.usedcar.mapper.AuditLogMapper;
import cn.edu.usedcar.model.Role; // 区分管理员和工作人员查看字段的范围。
import cn.edu.usedcar.model.SessionAccount; // 依据会话角色决定实际权限。
import com.baomidou.mybatisplus.core.toolkit.Wrappers; // 构建参数化日志筛选。
import com.fasterxml.jackson.annotation.JsonInclude; // 低权限响应隐藏未授权字段。
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation; // 日志分页两次读取使用一致快照。
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 统一规定审计记录与业务变更共用事务，由 Mapper 执行持久化。
 * @author pangzhihao
 */
@Service
public class AuditLogService {
    private static final Set<String> STAFF_SUBJECTS = Set.of("VEHICLE", "CONSIGNMENT", "REPORT", "APPOINTMENT",
            "ORDER", "LOAN", "REPAIR"); // 工作人员只看交易相关的结构化事件。
    private final AuditLogMapper auditLogs;
    private final AccountMapper accounts; // 提供当页日志的经办者显示名。

    public AuditLogService(AuditLogMapper auditLogs, AccountMapper accounts) {
        this.auditLogs = auditLogs;
        this.accounts = accounts; // 保存账号批量读取接口。
    }

    /** 系统操作可使用空的操作人。调用方提供可审计的业务说明，避免写入敏感凭据。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Long actorId, String action, String subjectType, long subjectId, String detail) {
        auditLogs.insertEvent(actorId, action, subjectType, subjectId, detail); // 保留既有业务事件的入库字段。
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ) // 总量、记录与操作人对应同一个只读快照。
    public PageResult<View> list(SessionAccount actor, AuditQuery request) {
        if (request.from() != null && request.to() != null && request.from().isAfter(request.to())) {
            throw BookingSupport.invalid("开始日期不能晚于结束日期。"); // 拒绝反向时间范围。
        }
        if (!supported(request.from()) || !supported(request.to())) {
            throw BookingSupport.invalid("日志日期超出可查询范围。"); // 使用数据库支持的主要日期区间。
        }
        boolean admin = actor.profile().roles().contains(Role.ADMIN); // 只有管理员能读取安全类日志和原文说明。
        var filter = Wrappers.lambdaQuery(AuditLogEntity.class)
                .in(!admin, AuditLogEntity::getSubjectType, STAFF_SUBJECTS) // 权限先于调用方提供的任何过滤条件。
                .eq(request.subjectType() != null, AuditLogEntity::getSubjectType, request.subjectType()) // 精确限定目标类型。
                .eq(request.action() != null, AuditLogEntity::getAction, request.action()) // 按具体操作代码查找。
                .eq(request.subjectId() != null, AuditLogEntity::getSubjectId, request.subjectId()) // 读取指定业务记录的历史。
                .ge(request.from() != null, AuditLogEntity::getCreatedAt, request.from() == null ? null : request.from().atStartOfDay()) // 开始日期从零点算起。
                .le(request.to() != null, AuditLogEntity::getCreatedAt, request.to() == null ? null : request.to().atTime(23, 59, 59)) // 截止日期包含当日全部秒值。
                .orderByDesc(AuditLogEntity::getId); // 固定按最近操作排序。
        var rows = auditLogs.selectPage(BookingSupport.page(request.page() == null ? 1 : request.page(),
                request.pageSize() == null ? 10 : request.pageSize()), filter); // 使用统一的分页校验。
        var ids = rows.getRecords().stream().map(AuditLogEntity::getActorId).filter(Objects::nonNull).distinct().toList(); // 系统操作可没有真人账号。
        Map<Long, String> names = ids.isEmpty() ? Map.of() : accounts.selectByIds(ids).stream()
                .collect(Collectors.toMap(UserAccountEntity::getId, UserAccountEntity::getDisplayName)); // 只取显示名，不读取到响应里的账号安全字段。
        return BookingSupport.views(rows, row -> new View(row.getId(), row.getActorId(), row.getActorId() == null ? "系统" : names.get(row.getActorId()),
                row.getAction(), row.getSubjectType(), row.getSubjectId(), admin ? row.getDetail() : null, row.getCreatedAt())); // 低权限时省略自由文本。
    }

    private static boolean supported(LocalDate date) {
        return date == null || (date.getYear() >= 1000 && date.getYear() <= 9999); // 两端筛选均遵守 MySQL 日期范围。
    }

    /** 已按请求账号角色裁剪的审计视图。 */
    @JsonInclude(JsonInclude.Include.NON_NULL) // 低权限响应不包含说明字段。
    public record View(
            long id, // 审计记录编号。
            Long actorId, // 执行者编号，系统操作可为空。
            String actorName, // 执行者显示名。
            String action, // 业务操作代码。
            String subjectType, // 被操作的记录类型。
            long subjectId, // 被操作的业务记录编号。
            String detail, // 管理员可看的原文说明。
            LocalDateTime createdAt // 记录入库时间。
    ) { }
}
