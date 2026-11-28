package cn.edu.usedcar.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import cn.edu.usedcar.common.ApiException;
import cn.edu.usedcar.common.PageResult;
import cn.edu.usedcar.entity.VehicleEntity;
import cn.edu.usedcar.entity.VehicleReportEntity;
import cn.edu.usedcar.mapper.ReportMapper;
import cn.edu.usedcar.mapper.VehicleMapper;
import cn.edu.usedcar.model.AttachmentInfo;
import cn.edu.usedcar.model.ReportDetails;
import cn.edu.usedcar.model.ReportDraft;
import cn.edu.usedcar.model.ReportStatus;
import cn.edu.usedcar.model.ReviewDecision;
import cn.edu.usedcar.model.SessionAccount;
import cn.edu.usedcar.model.UploadFile;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 车况报告登记、公开查询和审核。报告可多次提交，每一版保留审核结论。
 * @author pangzhihao
 */
@Service
public class ReportService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private final ReportMapper reports;
    private final VehicleMapper vehicles;
    private final VehicleMediaAccess access;
    private final AttachmentService attachments;
    private final AuditLogService audit;
    private final NotificationService notifications;

    ReportService(ReportMapper reports, VehicleMapper vehicles, VehicleMediaAccess access, AttachmentService attachments,
                  AuditLogService audit, NotificationService notifications) {
        this.reports = reports;
        this.vehicles = vehicles;
        this.access = access;
        this.attachments = attachments;
        this.audit = audit;
        this.notifications = notifications;
    }

    @Transactional
    public ReportDetails create(long vehicleId, long ownerId, ReportDraft draft, UploadFile file) {
        var vehicle = access.lockOwned(vehicleId, ownerId);
        VehicleMediaAccess.requireReportWritable(vehicle);
        if (draft.reportDate().isAfter(LocalDate.now(BUSINESS_ZONE))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "报告日期不能晚于今天。");
        }
        var report = new VehicleReportEntity();
        report.setVehicleId(vehicleId);
        report.setAuthorId(ownerId);
        report.setSource(draft.source().strip());
        report.setReportDate(draft.reportDate());
        report.setSummary(draft.summary().strip());
        report.setStatus(ReportStatus.PENDING);
        reports.insert(report);
        audit.record(ownerId, "SUBMIT_REPORT", "REPORT", report.getId(), "登记车况报告");
        // 同次提交中的附件失败时，报告、审计及文件一起回滚。
        var files = file == null ? List.<AttachmentInfo>of() : List.of(attachments.upload(vehicleId, ownerId, report.getId(), file));
        return details(report, vehicle.getModel(), files, true);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<ReportDetails> list(long vehicleId, SessionAccount actor) {
        var vehicle = access.readable(vehicleId, actor);
        boolean privateView = VehicleMediaAccess.privateView(vehicle, actor);
        var entries = reports.selectList(Wrappers.lambdaQuery(VehicleReportEntity.class)
                .eq(VehicleReportEntity::getVehicleId, vehicleId)
                .eq(!privateView, VehicleReportEntity::getStatus, ReportStatus.APPROVED)
                .orderByDesc(VehicleReportEntity::getReportDate, VehicleReportEntity::getId));
        var files = attachments.reportFiles(entries.stream().map(VehicleReportEntity::getId).toList());
        return entries.stream().map(row -> details(row, vehicle.getModel(),
                files.getOrDefault(row.getId(), List.of()), privateView)).toList();
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<ReportDetails> queue(ReportStatus status, int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "页码从 1 开始，每页最多 100 条。");
        }
        var result = reports.selectPage(new Page<>(page, pageSize), Wrappers.lambdaQuery(VehicleReportEntity.class)
                .eq(status != null, VehicleReportEntity::getStatus, status).orderByDesc(VehicleReportEntity::getId));
        var entries = result.getRecords();
        if (entries.isEmpty()) return new PageResult<>(List.of(), result.getTotal(), page, pageSize);

        var vehicleIds = entries.stream().map(VehicleReportEntity::getVehicleId).distinct().toList();
        Map<Long, String> models = vehicles.selectByIds(vehicleIds).stream()
                .collect(Collectors.toMap(VehicleEntity::getId, VehicleEntity::getModel));
        var files = attachments.reportFiles(entries.stream().map(VehicleReportEntity::getId).toList());
        var views = entries.stream().map(entry -> details(entry, models.get(entry.getVehicleId()),
                files.getOrDefault(entry.getId(), List.of()), true)).toList();
        return new PageResult<>(views, result.getTotal(), page, pageSize);
    }

    @Transactional
    public ReportDetails review(long reportId, long reviewerId, long revision, ReviewDecision decision, String note) {
        var current = reports.selectById(reportId);
        if (current == null) throw missing();
        var vehicle = access.lock(current.getVehicleId());
        var report = reports.selectForUpdate(reportId);
        if (report == null) throw missing();
        if (report.getStatus() != ReportStatus.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "REPORT_STATE_CONFLICT", "报告已处理，请刷新列表。");
        }
        List<AttachmentInfo> files = attachments.reportFiles(List.of(reportId)).getOrDefault(reportId, List.of());
        if (revision(files) != revision) {
            throw new ApiException(HttpStatus.CONFLICT, "STALE_REVIEW", "卖家已补充附件，请刷新后重新审核。");
        }
        String reason = note == null ? "" : note.strip();
        boolean approve = decision == ReviewDecision.APPROVE;
        if (!approve && reason.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "退回时必须填写原因。");
        }
        report.setStatus(approve ? ReportStatus.APPROVED : ReportStatus.REJECTED);
        report.setReviewerId(reviewerId);
        report.setReviewNote(reason.isEmpty() ? null : reason);
        report.setReviewedAt(LocalDateTime.now(BUSINESS_ZONE));
        reports.updateById(report);
        audit.record(reviewerId, approve ? "APPROVE_REPORT" : "REJECT_REPORT", "REPORT", reportId,
                reason.isEmpty() ? "报告审核通过" : reason);
        notifications.record(report.getAuthorId(), report.getVehicleId(), "report-review:" + reportId, "REPORT", reportId,
                approve ? "车况报告审核通过。" : "车况报告已退回，请查看审核说明。");
        return details(report, vehicle.getModel(), files, true);
    }

    private static long revision(List<AttachmentInfo> files) {
        return files.stream().mapToLong(AttachmentInfo::id).max().orElse(0);
    }

    private static ReportDetails details(VehicleReportEntity entry, String model, List<AttachmentInfo> files,
                                         boolean privateView) {
        return new ReportDetails(entry.getId(), entry.getVehicleId(), model, entry.getSource(), entry.getReportDate(),
                entry.getSummary(), entry.getStatus(), privateView ? entry.getReviewNote() : null,
                privateView ? entry.getReviewedAt() : null, privateView ? revision(files) : null, files);
    }

    private static ApiException missing() {
        return new ApiException(HttpStatus.NOT_FOUND, "REPORT_NOT_FOUND", "报告不存在或不可查看。");
    }
}
