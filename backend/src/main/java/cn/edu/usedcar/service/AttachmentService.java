package cn.edu.usedcar.service;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import cn.edu.usedcar.common.ApiException;
import cn.edu.usedcar.entity.AttachmentEntity;
import cn.edu.usedcar.mapper.AttachmentMapper;
import cn.edu.usedcar.mapper.ReportMapper;
import cn.edu.usedcar.model.AttachmentInfo;
import cn.edu.usedcar.model.AttachmentKind;
import cn.edu.usedcar.model.BusinessTime; // 下载使用与车源市场相同的业务时间。
import cn.edu.usedcar.model.ReportStatus;
import cn.edu.usedcar.model.SessionAccount;
import cn.edu.usedcar.model.UploadFile;
import cn.edu.usedcar.model.VehicleStatus;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 附件业务层，将所属车辆、资料状态、存储与下载权限统一校验。
 * @author pangzhihao
 */
@Service
public class AttachmentService {
    private final AttachmentMapper attachments;
    private final ReportMapper reports;
    private final VehicleMediaAccess access;
    private final AttachmentFileStorage storage;
    private final AuditLogService audit;

    AttachmentService(AttachmentMapper attachments, ReportMapper reports, VehicleMediaAccess access,
                      AttachmentFileStorage storage, AuditLogService audit) {
        this.attachments = attachments;
        this.reports = reports;
        this.access = access;
        this.storage = storage;
        this.audit = audit;
    }

    @Transactional
    public AttachmentInfo upload(long vehicleId, long ownerId, Long reportId, UploadFile file) {
        var vehicle = access.lockOwned(vehicleId, ownerId);
        boolean reportFile = reportId != null;
        if (reportFile) {
            VehicleMediaAccess.requireReportWritable(vehicle);
            var report = reports.selectForUpdate(reportId);
            if (report == null || report.getVehicleId() != vehicleId) {
                throw new ApiException(HttpStatus.NOT_FOUND, "REPORT_NOT_FOUND", "该车源下没有此报告。");
            }
            if (report.getStatus() != ReportStatus.PENDING) {
                throw new ApiException(HttpStatus.CONFLICT, "REPORT_STATE_CONFLICT", "仅待审核报告可以补充附件。");
            }
        } else {
            var status = vehicle.getStatus();
            if (status == VehicleStatus.LOCKED || status == VehicleStatus.SOLD
                    || (status == VehicleStatus.LISTED && access.isPublic(vehicleId))) { // 委托自然到期即视同下架，可补拍照片后重提。
                throw new ApiException(HttpStatus.CONFLICT, "VEHICLE_STATE_CONFLICT", "在售车源需下架后再补充照片。");
            }
        }

        var stored = storage.write(file, reportFile);
        var attachment = new AttachmentEntity();
        attachment.setVehicleId(vehicleId);
        attachment.setReportId(reportId);
        attachment.setUploaderId(ownerId);
        attachment.setKind(reportFile ? AttachmentKind.REPORT : AttachmentKind.PHOTO);
        attachment.setStorageKey(stored.key());
        attachment.setName(stored.name());
        attachment.setMime(stored.mime());
        attachment.setBytes(stored.bytes());
        attachments.insert(attachment);
        // 新照片使工作人员手中的上一版车源审核失效。
        audit.record(ownerId, reportFile ? "ADD_REPORT_ATTACHMENT" : "ADD_VEHICLE_PHOTO",
                reportFile ? "REPORT" : "VEHICLE", reportFile ? reportId : vehicleId, "新增附件：" + stored.name());
        return info(attachment);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<AttachmentInfo> photos(long vehicleId, SessionAccount actor) {
        access.readable(vehicleId, actor);
        return attachments.selectList(Wrappers.lambdaQuery(AttachmentEntity.class)
                .eq(AttachmentEntity::getVehicleId, vehicleId).eq(AttachmentEntity::getKind, AttachmentKind.PHOTO)
                .orderByAsc(AttachmentEntity::getId)).stream().map(AttachmentService::info).toList();
    }

    public Download download(long id, SessionAccount actor) {
        var attachment = attachments.selectReadable(id, VehicleMediaAccess.viewerId(actor), VehicleMediaAccess.manager(actor),
                BusinessTime.now()); // 让数据库同时判断报告和代销车的公开有效性。
        if (attachment == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "ATTACHMENT_NOT_FOUND", "附件不存在或不可查看。");
        }
        return new Download(storage.read(attachment.getStorageKey()), info(attachment),
                attachment.getKind() == AttachmentKind.PHOTO);
    }

    Map<Long, List<AttachmentInfo>> reportFiles(Collection<Long> reportIds) {
        if (reportIds.isEmpty()) return Map.of();
        return attachments.selectList(Wrappers.lambdaQuery(AttachmentEntity.class)
                .in(AttachmentEntity::getReportId, reportIds).eq(AttachmentEntity::getKind, AttachmentKind.REPORT)
                .orderByAsc(AttachmentEntity::getId)).stream().collect(Collectors.groupingBy(
                        AttachmentEntity::getReportId, Collectors.mapping(AttachmentService::info, Collectors.toList())));
    }

    private static AttachmentInfo info(AttachmentEntity item) {
        return new AttachmentInfo(item.getId(), item.getName(), item.getMime(), item.getBytes());
    }

    public record Download(Path path, AttachmentInfo info, boolean inline) {
    }
}
