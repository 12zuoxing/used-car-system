package cn.edu.usedcar.service;

import cn.edu.usedcar.mapper.AuditLogMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 统一规定审计记录与业务变更共用事务，由 Mapper 执行持久化。
 * @author pangzhihao
 */
@Service
public class AuditLogService {
    private final AuditLogMapper auditLogs;

    public AuditLogService(AuditLogMapper auditLogs) {
        this.auditLogs = auditLogs;
    }

    /** 系统操作可使用空的操作人。调用方提供可审计的业务说明，避免写入敏感凭据。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Long actorId, String action, String subjectType, long subjectId, String detail) {
        auditLogs.insert(actorId, action, subjectType, subjectId, detail);
    }
}
