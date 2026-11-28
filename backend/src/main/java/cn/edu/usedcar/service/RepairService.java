package cn.edu.usedcar.service; // 将售前整备和售后送修的业务规则集中在服务层。

import java.time.LocalDateTime; // 处理预计到店与实际完工时间。
import java.util.List; // 批量收集列表涉及的车辆和人员。
import java.util.Objects; // 从工单参与人中排除尚未接单的空值。
import java.util.stream.Stream; // 整理申请人与经办人编号。
import cn.edu.usedcar.common.ApiException; // 使用已有的统一业务错误结构。
import cn.edu.usedcar.common.PageResult; // 复用统一分页返回对象。
import cn.edu.usedcar.dto.RepairRequest; // 接收申请人在表单填写的字段。
import cn.edu.usedcar.dto.RepairStatusRequest; // 接收工作人员的办理动作与说明。
import cn.edu.usedcar.entity.RepairOrderEntity; // 持久化工单状态、车辆与预约资料。
import cn.edu.usedcar.entity.VehicleEntity; // 读取已锁定车源的状态与归属。
import cn.edu.usedcar.mapper.RepairMapper; // 提供工单持久化与分页。
import cn.edu.usedcar.mapper.TradeOrderMapper; // 核验售后车辆对应的成交订单。
import cn.edu.usedcar.model.BusinessTime; // 复用项目的北京时间与秒级精度。
import cn.edu.usedcar.model.RepairStage; // 指定工单属于售前或售后。
import cn.edu.usedcar.model.RepairStatus; // 约束工单状态流转。
import cn.edu.usedcar.model.TradeOrderStatus; // 只允许成交订单作为售后依据。
import cn.edu.usedcar.model.VehicleStatus; // 校验车辆是否还能申请售前整备。
import com.baomidou.mybatisplus.core.toolkit.Wrappers; // 组合参数化的状态与阶段筛选条件。
import org.springframework.http.HttpStatus; // 将资源缺失和越权映射到一致的 HTTP 状态。
import org.springframework.stereotype.Service; // 将工单业务注册为 Spring 服务。
import org.springframework.transaction.annotation.Isolation; // 保证加锁后能读取最新写入的数据。
import org.springframework.transaction.annotation.Transactional; // 使用事务统一工单、通知和审计的提交边界。

/** 根据车辆归属和成交订单办理售前与售后工单。
 * @author pangzhihao
 */
@Service // 由 Spring 管理实例并应用事务拦截。
public class RepairService {
    private final RepairMapper repairs; // 提供工单记录的读取和更新。
    private final TradeOrderMapper orders; // 从服务端获取真实的交易关系。
    private final VehicleMediaAccess vehicles; // 与订单和其他车辆业务共用车辆行锁。
    private final BookingSupport support; // 复用姓名查询、分页校验和文本处理。
    private final AuditLogService audit; // 记录申请、受理与完成操作。
    private final NotificationService notifications; // 将办理结果登记到申请人的通知中。

    public RepairService(RepairMapper repairs, TradeOrderMapper orders, VehicleMediaAccess vehicles,
                         BookingSupport support, AuditLogService audit, NotificationService notifications) {
        this.repairs = repairs; // 注入工单 Mapper。
        this.orders = orders; // 注入订单 Mapper。
        this.vehicles = vehicles; // 注入车辆权限与锁定组件。
        this.support = support; // 注入展示名称及分页组件。
        this.audit = audit; // 注入统一日志服务。
        this.notifications = notifications; // 注入通知服务。
    }

    @Transactional(isolation = Isolation.READ_COMMITTED) // 先锁车辆，保证售后成交状态与工单写入同步。
    public Detail create(long applicantId, RepairRequest form) {
        var vehicle = targetVehicle(applicantId, form); // 根据申请类型验证购买凭据或车源归属。
        var now = BusinessTime.now(); // 校验以取得锁后的时间为准。
        var visitAt = BusinessTime.toSecond(form.visitAt()); // 与数据库存储的秒精度保持一致。
        if (!visitAt.isAfter(now) || visitAt.getYear() > 9999) {
            throw BookingSupport.invalid("预计到店时间须晚于当前时间。"); // 拒绝过去时间和数据库无法保存的年份。
        }
        var row = new RepairOrderEntity(); // 创建新的工单记录。
        row.setVehicleId(vehicle.getId()); // 保存通过校验的车辆编号。
        row.setTradeOrderId(form.tradeOrderId()); // 售后保存经过校验的订单依据，售前留空。
        row.setApplicantId(applicantId); // 申请人必须是实际登录账号。
        row.setStage(form.stage()); // 保存已经通过资格检查的业务阶段。
        row.setVisitAt(visitAt); // 保存预计到店的北京时间。
        row.setDescription(form.description().strip()); // 清理用户登记的整备要求或故障描述。
        row.setStatus(RepairStatus.PENDING); // 新工单统一进入待受理。
        row.setCreatedAt(now); // 保存工单提交时刻。
        repairs.insert(row); // 持久化记录并获得工单编号。
        audit.record(applicantId, "SUBMIT_REPAIR", "REPAIR", row.getId(), "提交工单：" + row.getStage()); // 在审计中记录业务阶段，无需保存故障详情。
        return detailView(row); // 返回申请人可继续查看的单条记录。
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ) // 分页计数和业务列读取共享同一快照。
    public PageResult<Summary> mine(long applicantId, RepairStage stage, RepairStatus status, int page, int pageSize) {
        return page(applicantId, stage, status, page, pageSize); // 个人中心只查询会话用户发起的工单。
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ) // 保证后台分页数据和总量一致。
    public PageResult<Summary> queue(RepairStage stage, RepairStatus status, int page, int pageSize) {
        return page(null, stage, status, page, pageSize); // 工作人员可按任意工单阶段和办理状态查找。
    }

    @Transactional(readOnly = true)
    public Detail detail(long id, long applicantId) {
        var row = required(id); // 从工单编号定位详情。
        requireApplicant(row, applicantId); // 只有当前申请人可以通过个人接口读取。
        return detailView(row); // 返回授权后的工单描述和结果。
    }

    @Transactional(readOnly = true)
    public Detail staffDetail(long id) {
        return detailView(required(id)); // 后台控制器已按工作人员角色保护访问。
    }

    @Transactional(isolation = Isolation.READ_COMMITTED) // 与经办共用车辆行锁，避免受理和取消同时成功。
    public Detail cancel(long id, long applicantId) {
        vehicles.lock(vehicleId(id)); // 只根据稳定的车辆外键获取交易共用的锁。
        var row = required(id); // 拿到锁后重读最新状态。
        requireApplicant(row, applicantId); // 无关账号既不能查询也不能撤回工单。
        if (row.getStatus() != RepairStatus.PENDING) throw stateConflict("仅待受理工单可以取消。"); // 工作人员接单后由后台处理。
        row.setStatus(RepairStatus.CANCELLED); // 保存撤回的终局状态。
        row.setResultNote("申请人已取消工单。"); // 留下给后续查询用户和工作人员的原始原因。
        row.setCompletedAt(null); // 取消没有实际完工时间。
        repairs.updateById(row); // 保留原申请及业务关联。
        audit.record(applicantId, "CANCEL_REPAIR", "REPAIR", id, "申请人取消工单"); // 记录实际撤回人。
        return detailView(row); // 将更新后的状态返回个人页面。
    }

    @Transactional(isolation = Isolation.READ_COMMITTED) // 工作人员操作在车辆锁持有期间校验并提交。
    public Detail handle(long id, long staffId, RepairStatusRequest request) {
        var vehicle = vehicles.lock(vehicleId(id)); // 取得当前工单车辆的行锁。
        var row = required(id); // 获取在加锁前已提交的最新工单状态。
        var next = request.status(); // 读取明确选择的工作人员动作。
        if (!canMove(row.getStatus(), next)) throw stateConflict("工单状态已变化或不支持此操作。"); // 阻止跳步与重开已结案的工单。
        if (next == RepairStatus.PROCESSING && row.getStage() == RepairStage.PRE_SALE) {
            requirePreSaleVehicle(vehicle); // 受理前再次校验车辆仍然符合售前条件。
        }
        var note = BookingSupport.note(request.resultNote()); // 明确区分可选留空与真实填写的说明。
        if (next != RepairStatus.PROCESSING && note == null) throw BookingSupport.invalid("完工或取消时请填写办理说明。"); // 结案必须留有可查依据。
        row.setStatus(next); // 写入经过验证的下一状态。
        row.setHandlerId(staffId); // 记录本次操作的真实工作人员。
        row.setResultNote(note); // 保存工作人员向申请人说明的结果。
        row.setCompletedAt(next == RepairStatus.COMPLETED ? BusinessTime.now() : null); // 仅登记完工时写入实际完成时刻。
        repairs.updateById(row); // 更新工单及对应的办理数据。
        audit.record(staffId, "HANDLE_REPAIR", "REPAIR", id, "工单状态：" + next); // 记录处理动作而非详细故障描述。
        notifications.record(row.getApplicantId(), row.getVehicleId(), "repair:" + id + ":" + next,
                "REPAIR", id, message(next)); // 与工单状态同事务登记通知。
        return detailView(row); // 让经办页面展示已经保存的实际结果。
    }

    private VehicleEntity targetVehicle(long applicantId, RepairRequest form) {
        if (form.stage() == RepairStage.PRE_SALE) {
            if (form.tradeOrderId() != null) throw BookingSupport.invalid("售前整备只需绑定车源。"); // 售前记录不接受订单引用。
            var vehicle = vehicles.lockOwned(form.vehicleId(), applicantId); // 校验车辆属于提交申请的发布人。
            requirePreSaleVehicle(vehicle); // 订单锁定或成交的车辆已不再接受售前整备。
            return vehicle; // 返回满足售前前提的车源。
        }
        if (form.tradeOrderId() == null) throw BookingSupport.invalid("售后送修需要选择已成交订单。"); // 售后必须明确关联成交依据。
        var orderedVehicleId = orders.vehicleId(form.tradeOrderId()); // 用订单中的不可变外键确定锁定目标。
        if (orderedVehicleId == null) throw orderMissing(); // 不可以引用不存在或不可见的订单。
        var vehicle = vehicles.lock(orderedVehicleId); // 与过户及付款操作统一按实际车辆锁定。
        var order = orders.selectById(form.tradeOrderId()); // 在车辆锁内读取成交状态。
        if (order == null || order.getBuyerId() != applicantId) throw orderMissing(); // 仅原订单买方具备售后资格。
        if (!order.getVehicleId().equals(form.vehicleId())) throw BookingSupport.invalid("售后工单车辆须与成交订单一致。"); // 拒绝跨车辆引用。
        if (order.getStatus() != TradeOrderStatus.COMPLETED) throw stateConflict("仅已成交订单可以申请售后送修。"); // 付款完成仍需工作人员确认成交。
        return vehicle; // 绑定真实订单车辆而非未经验证的前端编号。
    }

    private static void requirePreSaleVehicle(VehicleEntity vehicle) {
        if (vehicle.getStatus() == VehicleStatus.LOCKED || vehicle.getStatus() == VehicleStatus.SOLD) {
            throw stateConflict("交易锁定或已售车辆无法受理售前整备。"); // 共享车辆锁之后检查实时状态。
        }
    }

    private static boolean canMove(RepairStatus current, RepairStatus next) {
        return switch (current) {
            case PENDING -> next == RepairStatus.PROCESSING || next == RepairStatus.CANCELLED; // 已收到的工单可受理或说明取消。
            case PROCESSING -> next == RepairStatus.COMPLETED || next == RepairStatus.CANCELLED; // 已受理的工单可完工或说明取消。
            case COMPLETED, CANCELLED -> false; // 已结案的记录保留历史。
        };
    }

    private static String message(RepairStatus status) {
        return switch (status) {
            case PROCESSING -> "工单已受理，请查看办理进度。"; // 告知申请人工作人员已接单。
            case COMPLETED -> "工单已完工，请查看办理结果。"; // 提醒申请人查看完工说明。
            case CANCELLED -> "工作人员已取消工单，请查看原因。"; // 将详细原因留在授权详情。
            case PENDING -> throw new IllegalStateException("待受理不是经办结果"); // 后台无法把其他状态改回初始状态。
        };
    }

    private PageResult<Summary> page(Long applicantId, RepairStage stage, RepairStatus status, int page, int pageSize) {
        var query = Wrappers.lambdaQuery(RepairOrderEntity.class)
                .eq(applicantId != null, RepairOrderEntity::getApplicantId, applicantId) // 个人查询按申请人过滤。
                .eq(stage != null, RepairOrderEntity::getStage, stage) // 通过工单类型区分售前与售后。
                .eq(status != null, RepairOrderEntity::getStatus, status) // 可只查看选中的办理状态。
                .orderByDesc(RepairOrderEntity::getId); // 使用固定顺序，保证历史分页稳定。
        var result = repairs.selectPage(BookingSupport.page(page, pageSize), query); // 由 MyBatis-Plus 获取一页工单。
        var names = support.names(result.getRecords().stream().map(RepairOrderEntity::getVehicleId).distinct().toList(),
                result.getRecords().stream().map(RepairOrderEntity::getApplicantId).distinct().toList()); // 批量取得当前页的车名与申请人。
        return BookingSupport.views(result, row -> summary(row, names)); // 提供简洁的工单列表摘要。
    }

    private static Summary summary(RepairOrderEntity row, BookingSupport.Names names) {
        return new Summary(row.getId(), row.getVehicleId(), names.vehicles().get(row.getVehicleId()), row.getTradeOrderId(),
                names.people().get(row.getApplicantId()), row.getStage(), row.getStatus(), row.getVisitAt()); // 让列表显示识别工单与进度所需字段。
    }

    private Detail detailView(RepairOrderEntity row) {
        var people = Stream.of(row.getApplicantId(), row.getHandlerId()).filter(Objects::nonNull).distinct().toList(); // 有受理人时再读取其显示名。
        var names = support.names(List.of(row.getVehicleId()), people); // 一次取得车型与联系人显示名。
        var item = summary(row, names); // 详情和摘要的业务列保持一致。
        var handler = row.getHandlerId() == null ? null : names.people().get(row.getHandlerId()); // 仅返回已存在的经办人。
        return new Detail(item.id(), item.vehicleId(), item.vehicleModel(), item.tradeOrderId(), item.applicantName(),
                handler, item.stage(), item.status(), item.visitAt(), row.getDescription(), row.getResultNote(),
                row.getCreatedAt(), row.getCompletedAt()); // 授权详情提供申请描述、办理说明和时间。
    }

    private long vehicleId(long id) {
        var vehicleId = repairs.vehicleId(id); // 读不会变化的车辆编号，完整工单稍后读取。
        if (vehicleId == null) throw missing(); // 缺失工单没有可锁定的目标。
        return vehicleId; // 将合法车辆编号交给统一加锁逻辑。
    }

    private RepairOrderEntity required(long id) {
        var row = repairs.selectById(id); // 使用工单编号读取最新状态。
        if (row == null) throw missing(); // 缺失工单统一返回 404。
        return row; // 将加载的工单交给调用路径核对权限。
    }

    private static void requireApplicant(RepairOrderEntity row, long applicantId) {
        if (row.getApplicantId() != applicantId) throw missing(); // 其他用户不能通过响应判断工单是否存在。
    }

    private static ApiException missing() {
        return new ApiException(HttpStatus.NOT_FOUND, "REPAIR_NOT_FOUND", "工单不存在或不可查看。"); // 隐藏未授权资源。
    }

    private static ApiException orderMissing() {
        return new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "订单不存在或不可查看。"); // 仅向原买方开放售后依据。
    }

    private static ApiException stateConflict(String message) {
        return BookingSupport.conflict("REPAIR_STATE_CONFLICT", message); // 区分状态冲突与普通字段错误。
    }

    /** 列表工单摘要，完整描述在权限校验后的详情中读取。 */
    public record Summary(
            long id, // 工单编号。
            long vehicleId, // 对应车源编号。
            String vehicleModel, // 列表显示的车辆名称。
            Long tradeOrderId, // 售后所依据的成交订单编号。
            String applicantName, // 申请人显示名称。
            RepairStage stage, // 售前或售后工单。
            RepairStatus status, // 当前办理状态。
            LocalDateTime visitAt // 预计到店时间。
    ) { }

    /** 买家、原申请车主或工作人员获准查看的工单详情。 */
    public record Detail(
            long id, // 工单编号。
            long vehicleId, // 车辆编号。
            String vehicleModel, // 车辆显示名称。
            Long tradeOrderId, // 售后成交依据。
            String applicantName, // 工单申请人。
            String handlerName, // 最后一次办理人员。
            RepairStage stage, // 工单业务阶段。
            RepairStatus status, // 当前办理进度。
            LocalDateTime visitAt, // 预计到店时刻。
            String description, // 用户登记的检查或故障描述。
            String resultNote, // 工单的办理说明或取消原因。
            LocalDateTime createdAt, // 申请提交时间。
            LocalDateTime completedAt // 实际完工时间。
    ) { }
}
