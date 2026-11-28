package cn.edu.usedcar.service;

import java.time.LocalDateTime;
import java.time.Year;
import java.time.ZoneId;

import cn.edu.usedcar.common.ApiException;
import cn.edu.usedcar.common.PageResult;
import cn.edu.usedcar.entity.VehicleEntity;
import cn.edu.usedcar.mapper.VehicleMapper;
import cn.edu.usedcar.model.ReviewDecision;
import cn.edu.usedcar.model.SaleMode;
import cn.edu.usedcar.model.VehicleDraft;
import cn.edu.usedcar.model.VehicleFilter;
import cn.edu.usedcar.model.VehicleRecord;
import cn.edu.usedcar.model.VehicleStatus;
import cn.edu.usedcar.model.VehicleSubmission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 车源业务层。公开读取限定上架状态，发布、审核、重提和下架由事务集中管理。
 * @author pangzhihao
 */
@Service
public class VehicleService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private final VehicleMapper vehicles;
    private final DictionaryService dictionaries;
    private final AuditLogService audit;
    private final NotificationService notifications;
    private final AttachmentService attachments;
    private final ReportService reports;

    public VehicleService(VehicleMapper vehicles, DictionaryService dictionaries,
                          AuditLogService audit, NotificationService notifications, AttachmentService attachments,
                          ReportService reports) {
        this.vehicles = vehicles;
        this.dictionaries = dictionaries;
        this.audit = audit;
        this.notifications = notifications;
        this.attachments = attachments;
        this.reports = reports;
    }

    // MyBatis-Plus 分页先查总数再查记录，两次读取需要使用同一快照。
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<VehicleRecord> search(VehicleFilter filter) {
        validateRange(filter.minPrice(), filter.maxPrice(), "售价");
        validateRange(filter.minYear(), filter.maxYear(), "登记年份");
        validateRange(filter.minMileage(), filter.maxMileage(), "里程");
        return page(filter.page() == null ? 1 : filter.page(), filter.pageSize() == null ? 12 : filter.pageSize(),
                filter, VehicleStatus.LISTED, null, false);
    }

    public VehicleRecord publicDetail(long id) {
        return requiredView(vehicles.selectView(id, VehicleStatus.LISTED, null, false));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<VehicleRecord> mine(long ownerId, VehicleStatus status, int page, int pageSize) {
        return page(page, pageSize, null, status, ownerId, true);
    }

    public VehicleRecord myDetail(long id, long ownerId) {
        return requiredView(vehicles.selectView(id, null, ownerId, true));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<VehicleRecord> reviewQueue(VehicleStatus status, int page, int pageSize) {
        return page(page, pageSize, null, status, null, true);
    }

    @Transactional
    public VehicleRecord create(long ownerId, VehicleSubmission submission) {
        var draft = submission.vehicle();
        validateDraft(draft);
        var vehicle = new VehicleEntity();
        vehicle.setOwnerId(ownerId);
        applyDraft(vehicle, draft);
        vehicle.setStatus(VehicleStatus.PENDING);
        vehicles.insert(vehicle);
        audit.record(ownerId, "SUBMIT_VEHICLE", "VEHICLE", vehicle.getId(), "提交车源审核");
        saveMedia(vehicle.getId(), ownerId, submission);
        return myDetail(vehicle.getId(), ownerId);
    }

    @Transactional
    public VehicleRecord resubmit(long id, long ownerId, VehicleSubmission submission) {
        var draft = submission.vehicle();
        var vehicle = lockOwnedVehicle(id, ownerId);
        if (vehicle.getStatus() != VehicleStatus.REJECTED && vehicle.getStatus() != VehicleStatus.OFFLINE) {
            throw conflict("VEHICLE_STATE_CONFLICT", "仅已退回或已下架的车源可以修改并重新提交。");
        }
        validateDraft(draft);
        applyDraft(vehicle, draft);
        vehicle.setStatus(VehicleStatus.PENDING);
        vehicle.setReviewNote(null);
        vehicle.setReviewerId(null);
        vehicle.setReviewedAt(null);
        vehicle.setListedAt(null);
        vehicles.updateById(vehicle);
        audit.record(ownerId, "SUBMIT_VEHICLE", "VEHICLE", id, "整理资料后重新提交审核");
        saveMedia(id, ownerId, submission);
        return myDetail(id, ownerId);
    }

    @Transactional
    public VehicleRecord review(long id, long reviewerId, long revision, ReviewDecision decision, String note) {
        var vehicle = vehicles.selectForUpdate(id);
        if (vehicle == null) {
            throw missing();
        }
        if (vehicle.getStatus() != VehicleStatus.PENDING) {
            throw conflict("VEHICLE_STATE_CONFLICT", "仅待审核车源可以审核，请刷新列表。");
        }
        // 审计编号在每次提交时递增，即使车源重新回到待审，旧审核请求也不能生效。
        if (requiredView(vehicles.selectView(id, null, null, true)).revision() != revision) {
            throw conflict("STALE_REVIEW", "车源已重新提交，请刷新后重新审核。");
        }
        String reason = note == null ? "" : note.strip();
        boolean approve = decision == ReviewDecision.APPROVE;
        if (!approve && reason.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "退回时必须填写原因。");
        }
        if (approve) {
            if (vehicle.getSaleMode() == SaleMode.CONSIGN) {
                throw conflict("CONSIGNMENT_REQUIRED", "代销车源需完成委托流程后才能上架。");
            }
            dictionaries.requireActiveEntries(vehicle.getBrandId(), vehicle.getCityId());
            vehicle.setListedAt(LocalDateTime.now(BUSINESS_ZONE));
        }
        vehicle.setStatus(approve ? VehicleStatus.LISTED : VehicleStatus.REJECTED);
        vehicle.setReviewerId(reviewerId);
        vehicle.setReviewedAt(LocalDateTime.now(BUSINESS_ZONE));
        vehicle.setReviewNote(reason.isEmpty() ? null : reason);
        vehicles.updateById(vehicle);
        audit.record(reviewerId, approve ? "APPROVE_VEHICLE" : "REJECT_VEHICLE", "VEHICLE", id,
                reason.isEmpty() ? "审核通过并上架" : reason);
        notifications.record(vehicle.getOwnerId(), id, "vehicle-review:" + id + ":" + revision, "VEHICLE", id,
                approve ? "车源审核通过，已上架。" : "车源审核已退回，请在我的车源中查看原因。");
        return requiredView(vehicles.selectView(id, null, null, true));
    }

    @Transactional
    public VehicleRecord takeOffline(long id, long ownerId) {
        var vehicle = lockOwnedVehicle(id, ownerId);
        if (vehicle.getStatus() != VehicleStatus.LISTED) {
            throw conflict("VEHICLE_STATE_CONFLICT", "当前状态下不能下架。");
        }
        vehicle.setStatus(VehicleStatus.OFFLINE);
        vehicles.updateById(vehicle);
        audit.record(ownerId, "OFFLINE_VEHICLE", "VEHICLE", id, "发布人主动下架车源");
        return myDetail(id, ownerId);
    }

    private PageResult<VehicleRecord> page(int page, int pageSize, VehicleFilter filter, VehicleStatus status,
                                           Long ownerId, boolean managed) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "页码从 1 开始，每页最多 100 条。");
        }
        var result = vehicles.selectVehiclePage(new Page<>(page, pageSize), filter, status, ownerId, managed);
        return new PageResult<>(result.getRecords(), result.getTotal(), page, pageSize);
    }

    private VehicleEntity lockOwnedVehicle(long id, long ownerId) {
        var vehicle = vehicles.selectForUpdate(id);
        // 查询自己的车源时用相同的 404 表示不存在和无权访问。
        if (vehicle == null || vehicle.getOwnerId() != ownerId) {
            throw missing();
        }
        return vehicle;
    }

    /** 资料随车源事务提交，避免待审车源先出现而附件尚未完成。 */
    private void saveMedia(long vehicleId, long ownerId, VehicleSubmission submission) {
        if (submission.reportFile() != null && submission.report() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "添加报告附件前请填写报告信息。");
        }
        if (submission.photo() != null) attachments.upload(vehicleId, ownerId, null, submission.photo());
        if (submission.report() != null) reports.create(vehicleId, ownerId, submission.report(), submission.reportFile());
    }

    private void validateDraft(VehicleDraft draft) {
        if (draft.registrationYear() > Year.now(BUSINESS_ZONE).getValue()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "登记年份不能晚于当前年份。");
        }
        dictionaries.requireActiveEntries(draft.brandId(), draft.cityId());
    }

    private static void applyDraft(VehicleEntity vehicle, VehicleDraft draft) {
        vehicle.setBrandId(draft.brandId());
        vehicle.setCityId(draft.cityId());
        vehicle.setModel(draft.model().strip());
        vehicle.setRegistrationYear(draft.registrationYear());
        vehicle.setMileage(draft.mileage());
        vehicle.setPrice(draft.price());
        vehicle.setTransmission(draft.transmission());
        vehicle.setFuel(draft.fuel());
        vehicle.setSaleMode(draft.saleMode());
        vehicle.setDescription(draft.description().strip());
    }

    private static <T extends Comparable<T>> void validateRange(T minimum, T maximum, String field) {
        if (minimum != null && maximum != null && minimum.compareTo(maximum) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_INPUT", field + "最小值不能大于最大值。");
        }
    }

    private static VehicleRecord requiredView(VehicleRecord record) {
        if (record == null) {
            throw missing();
        }
        return record;
    }

    private static ApiException missing() {
        return new ApiException(HttpStatus.NOT_FOUND, "VEHICLE_NOT_FOUND", "车源不存在或不可查看。");
    }

    private static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }
}
