package cn.edu.usedcar.service; // 代销相关的校验和状态流转集中在业务层。

import java.math.BigDecimal; // 以十进制定点金额返回服务费用。
import java.time.LocalDateTime; // 处理开始、截止和审核时间。
import java.util.List; // 保存可到期的委托状态集合。
import cn.edu.usedcar.common.ApiException; // 通过统一异常传递业务失败原因。
import cn.edu.usedcar.common.PageResult; // 返回列表、总数和页码。
import cn.edu.usedcar.dto.ConsignmentRequest; // 接收卖家允许提交的委托信息。
import cn.edu.usedcar.dto.ConsignmentReviewRequest; // 接收工作人员的审核结果。
import cn.edu.usedcar.entity.ConsignmentEntity; // 读写委托表实体。
import cn.edu.usedcar.entity.VehicleEntity; // 使用锁定后的车辆状态和归属。
import cn.edu.usedcar.mapper.ConsignmentMapper; // 调用委托及在办订单的持久化查询。
import cn.edu.usedcar.mapper.VehicleMapper; // 更新撤回或续签时需要下架的车辆。
import cn.edu.usedcar.model.BusinessTime; // 统一使用北京时间和秒级精度。
import cn.edu.usedcar.model.ConsignmentStatus; // 管理委托审核与终止状态。
import cn.edu.usedcar.model.ReviewDecision; // 判断审核通过还是退回。
import cn.edu.usedcar.model.SaleMode; // 区分直销和代销。
import cn.edu.usedcar.model.VehicleStatus; // 检查上架、锁定和售出状态。
import com.baomidou.mybatisplus.core.toolkit.Wrappers; // 组合参数化的 MyBatis-Plus 条件。
import org.springframework.http.HttpStatus; // 声明缺失和权限问题的响应状态。
import org.springframework.stereotype.Service; // 注册代销业务服务。
import org.springframework.transaction.annotation.Isolation; // 为分页的多次查询提供一致快照。
import org.springframework.transaction.annotation.Propagation; // 要求订单入口复用外部事务和车辆锁。
import org.springframework.transaction.annotation.Transactional; // 原子处理委托、车辆、通知和审计。

/** 代销委托提交、审核、撤回以及交易资格检查。
 * @author pangzhihao
 */
@Service // 由 Spring 管理业务层实例和事务。
public class ConsignmentService {
    private static final List<ConsignmentStatus> OPEN = List.of(ConsignmentStatus.PENDING, ConsignmentStatus.APPROVED); // 两种委托可以占用同一车辆的申请名额。
    private final ConsignmentMapper consignments; // 负责委托数据和活动订单计数。
    private final VehicleMapper vehicles; // 负责车辆状态写入。
    private final VehicleMediaAccess access; // 复用车辆查找、归属校验和行锁。
    private final BookingSupport support; // 复用分页和展示名称查询。
    private final AuditLogService audit; // 保存委托操作责任和时间。
    private final NotificationService notifications; // 登记卖家可读取的审核通知。

    public ConsignmentService(ConsignmentMapper consignments, VehicleMapper vehicles, VehicleMediaAccess access,
                              BookingSupport support, AuditLogService audit, NotificationService notifications) {
        this.consignments = consignments; // 注入委托 Mapper。
        this.vehicles = vehicles; // 注入车辆 Mapper。
        this.access = access; // 注入车辆权限和锁定组件。
        this.support = support; // 注入分页辅助组件。
        this.audit = audit; // 注入审计服务。
        this.notifications = notifications; // 注入通知服务。
    }

    @Transactional(isolation = Isolation.READ_COMMITTED) // 锁车源后读取此前已提交的委托。
    public View create(long vehicleId, long ownerId, ConsignmentRequest request) {
        var vehicle = access.lockOwned(vehicleId, ownerId); // 对同一车辆的提交和审核使用相同的行锁。
        requireWritable(vehicle); // 确认这辆代销车当前允许处理委托。
        var now = BusinessTime.now(); // 取得锁后时刻，避免在等待期间跨过截止边界。
        var start = BusinessTime.toSecond(request.startsAt()); // 与数据库秒精度对齐。
        var end = BusinessTime.toSecond(request.expiresAt()); // 截止判断和最终入库使用同一个值。
        if (start.getYear() < 1000 || end.getYear() > 9999 || !end.isAfter(start) || !end.isAfter(now)) {
            throw BookingSupport.invalid("委托截止时间须晚于开始时间和当前时间。"); // 拒绝无效区间和数据库不能保存的时间。
        }
        if (openCount(vehicleId, now) > 0) {
            throw BookingSupport.conflict("CONSIGNMENT_EXISTS", "该车已有待审或未到期的委托。"); // 每车只保留一份待审或未到期的委托。
        }
        if (vehicle.getStatus() == VehicleStatus.LISTED) {
            offline(vehicle); // 旧委托已失效，续签时重置上架状态，重新审核车源后方可公开。
        }
        var row = new ConsignmentEntity(); // 准备新增委托表记录。
        row.setVehicleId(vehicleId); // 保存路径绑定的车辆。
        row.setOwnerId(ownerId); // 保存登录会话中的车主。
        row.setStartsAt(start); // 保存有效期起点。
        row.setExpiresAt(end); // 保存有效期终点。
        row.setFee(request.fee()); // 保存校验过的人民币费用。
        row.setTerms(BookingSupport.note(request.terms())); // 清理可选的委托条款。
        row.setStatus(ConsignmentStatus.PENDING); // 新申请统一进入待审核。
        row.setCreatedAt(now); // 记录北京时间，避免依赖数据库主机时区。
        consignments.insert(row); // 插入记录并回填自增编号。
        audit.record(ownerId, "SUBMIT_CONSIGNMENT", "CONSIGNMENT", row.getId(), "提交代销委托"); // 保存提交审计轨迹。
        return view(row, now); // 返回卖家可以立即显示的委托详情。
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ) // 列表计数及详情共用数据库快照。
    public PageResult<View> mine(long vehicleId, long ownerId, int page, int size) {
        var vehicle = vehicles.selectById(vehicleId); // 只读取归属，无需写锁。
        if (vehicle == null || vehicle.getOwnerId() != ownerId) {
            throw new ApiException(HttpStatus.NOT_FOUND, "VEHICLE_NOT_FOUND", "车源不存在或不可查看。"); // 用相同响应隐藏他人的车源。
        }
        return page(vehicleId, ownerId, null, page, size); // 仅返回本人车辆的委托历史。
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ) // 保持筛选总数和页记录一致。
    public PageResult<View> queue(ConsignmentStatus status, int page, int size) {
        return page(null, null, status, page, size); // 工作人员可按有效状态跨车辆查看。
    }

    @Transactional(isolation = Isolation.READ_COMMITTED) // 锁到车辆后重读委托，读取并发操作的最新提交结果。
    public View review(long id, long reviewerId, ConsignmentReviewRequest request) {
        var vehicle = access.lock(vehicleId(id)); // 按车辆 → 委托的顺序处理，兼容下单锁。
        var row = required(id); // 获得车辆锁后读取委托的最新状态。
        var now = BusinessTime.now(); // 使用审核发生时的时间验证。
        if (status(row, now) != ConsignmentStatus.PENDING) {
            throw BookingSupport.conflict("CONSIGNMENT_STATE_CONFLICT", "仅未到期的待审委托可以审核。"); // 阻止重复审核与过期批准。
        }
        var note = BookingSupport.note(request.note()); // 清理说明文本，判断是否为实际填写。
        boolean approve = request.decision() == ReviewDecision.APPROVE; // 判断本次需要审核通过还是退回。
        if (approve) {
            requireWritable(vehicle); // 审批时再次检查代销方式与车辆状态。
            if (consignments.selectCount(Wrappers.lambdaQuery(ConsignmentEntity.class)
                    .eq(ConsignmentEntity::getVehicleId, vehicle.getId()) // 同一车源才可能发生重复生效。
                    .eq(ConsignmentEntity::getStatus, ConsignmentStatus.APPROVED) // 审核通过的历史登记可能仍处于有效期限。
                    .gt(ConsignmentEntity::getExpiresAt, now)) > 0) {
                throw BookingSupport.conflict("CONSIGNMENT_EXISTS", "该车已有未到期的已通过委托。"); // 老数据也不能通过审核产生两份活动委托。
            }
        }
        if (!approve && note == null) throw BookingSupport.invalid("退回委托时请填写原因。"); // 确保卖家能查看退回原因。
        row.setStatus(approve ? ConsignmentStatus.APPROVED : ConsignmentStatus.REJECTED); // 保存审核后的状态。
        row.setReviewerId(reviewerId); // 保存经办人员账号。
        row.setReviewNote(note); // 保存审核说明。
        row.setReviewedAt(now); // 保存审核发生的北京时间。
        consignments.updateById(row); // 委托只允许从待审核进入审核结果。
        audit.record(reviewerId, approve ? "APPROVE_CONSIGNMENT" : "REJECT_CONSIGNMENT", "CONSIGNMENT", id,
                approve ? "通过代销委托" : "退回代销委托"); // 在同一事务保存审核记录。
        notifications.record(row.getOwnerId(), row.getVehicleId(), "consignment-review:" + id, "CONSIGNMENT", id,
                approve ? "委托已通过审核，请等待生效并完成车源审核。" : "委托已退回，请查看原因。"); // 告知卖家下一步。
        return view(row, now); // 返回刚写入的审核结果。
    }

    @Transactional(isolation = Isolation.READ_COMMITTED) // 车辆锁后检查当前委托与订单，避免和下单交叉写入。
    public View withdraw(long id, long ownerId) {
        var vehicle = access.lock(vehicleId(id)); // 找到车辆并加锁，再读取最新的车主和委托状态。
        if (vehicle.getOwnerId() != ownerId) throw missing(); // 对他人的委托返回相同的缺失结果。
        var row = required(id); // 获得车辆锁后读取委托状态。
        var now = BusinessTime.now(); // 按撤回请求处理时的北京时间判断。
        if (!OPEN.contains(status(row, now))) {
            throw BookingSupport.conflict("CONSIGNMENT_STATE_CONFLICT", "仅待审或未到期的委托可撤回。"); // 保留已终止的历史记录。
        }
        if (vehicle.getStatus() == VehicleStatus.LOCKED || vehicle.getStatus() == VehicleStatus.SOLD
                || consignments.countInProgressOrders(vehicle.getId()) > 0) {
            throw BookingSupport.conflict("CONSIGNMENT_HAS_ORDER", "车辆有在办交易或已售出，无法撤回委托。"); // 保留交易依赖的委托。
        }
        row.setStatus(ConsignmentStatus.WITHDRAWN); // 保存卖家撤回的最终状态。
        consignments.updateById(row); // 在同一事务写入委托。
        if (vehicle.getSaleMode() == SaleMode.CONSIGN && vehicle.getStatus() == VehicleStatus.LISTED) {
            offline(vehicle); // 同步下架已失去有效委托的代销车。
        }
        audit.record(ownerId, "WITHDRAW_CONSIGNMENT", "CONSIGNMENT", id, "撤回代销委托"); // 保存卖家的操作记录。
        return view(row, now); // 返回撤回结果用于刷新页面。
    }

    @Transactional(propagation = Propagation.MANDATORY) // 调用方的下单事务须保持车辆锁直到订单写完。
    public Long requireForOrder(long vehicleId) {
        var vehicle = access.lock(vehicleId); // 获取与撤回和审核使用的同一车辆行锁。
        if (vehicle.getStatus() != VehicleStatus.LISTED) {
            throw BookingSupport.conflict("VEHICLE_STATE_CONFLICT", "仅在售车辆可以下单。"); // 锁定和未上架车辆均不能下单。
        }
        return requireActive(vehicle, BusinessTime.now()); // 直销返回空，代销返回必须写入订单的委托编号。
    }

    boolean isEligible(VehicleEntity vehicle, LocalDateTime now) {
        return vehicle.getSaleMode() == SaleMode.DIRECT // 直销没有代销期限限制。
                || consignments.activeId(vehicle.getId(), vehicle.getOwnerId(), now) != null; // 代销必须存在当前有效的同车同车主委托。
    }

    Long requireActive(VehicleEntity vehicle, LocalDateTime now) {
        if (vehicle.getSaleMode() == SaleMode.DIRECT) return null; // 直销订单不绑定委托记录。
        var active = consignments.activeId(vehicle.getId(), vehicle.getOwnerId(), now); // 一次查询核对有效期、状态和归属。
        if (active == null) {
            throw BookingSupport.conflict("CONSIGNMENT_REQUIRED", "代销车源需要当前有效的委托。"); // 向审核和下单入口返回统一阻止原因。
        }
        return active; // 返回业务操作实际使用的委托编号。
    }

    void requireModeChangeAllowed(VehicleEntity vehicle, SaleMode next, LocalDateTime now) {
        if (vehicle.getSaleMode() != next && openCount(vehicle.getId(), now) > 0) {
            throw BookingSupport.conflict("CONSIGNMENT_EXISTS", "请先撤回未结束的委托，再修改销售方式。"); // 避免旧委托跟随新销售方式继续生效。
        }
    }

    private long openCount(long vehicleId, LocalDateTime now) {
        return consignments.selectCount(Wrappers.lambdaQuery(ConsignmentEntity.class) // 只计算当前阻塞新申请的记录。
                .eq(ConsignmentEntity::getVehicleId, vehicleId) // 只检查被锁车辆的数据。
                .in(ConsignmentEntity::getStatus, OPEN) // 待审与已批准状态均占用申请名额。
                .gt(ConsignmentEntity::getExpiresAt, now)); // 截止时刻一到即可提交新申请。
    }

    private void offline(VehicleEntity vehicle) {
        vehicle.setStatus(VehicleStatus.OFFLINE); // 将车辆变更为已下架。
        vehicle.setListedAt(null); // 清除旧展示时间，重新上架时重新计时。
        vehicles.updateById(vehicle); // 复用车辆实体的空值更新策略。
    }

    private static void requireWritable(VehicleEntity vehicle) {
        if (vehicle.getSaleMode() != SaleMode.CONSIGN || vehicle.getStatus() == VehicleStatus.LOCKED
                || vehicle.getStatus() == VehicleStatus.SOLD) {
            throw BookingSupport.conflict("VEHICLE_STATE_CONFLICT", "仅未锁定且未售出的代销车源可以办理委托。"); // 排除与委托无关的车或在办交易。
        }
    }

    private ConsignmentEntity required(long id) {
        var row = consignments.selectById(id); // 通过主键定位需要处理的委托。
        if (row == null) throw missing(); // 查不到主记录时终止处理。
        return row; // 返回委托数据给已开始的业务流程。
    }

    private long vehicleId(long id) {
        var vehicleId = consignments.vehicleId(id); // 先读取不变的车辆编号，避免缓存旧的审核状态。
        if (vehicleId == null) throw missing(); // 目标不存在时无法取得车辆锁。
        return vehicleId; // 返回对应车辆作为锁定对象。
    }

    private static ApiException missing() {
        return new ApiException(HttpStatus.NOT_FOUND, "CONSIGNMENT_NOT_FOUND", "委托不存在或不可查看。"); // 统一记录缺失和越权响应。
    }

    private PageResult<View> page(Long vehicleId, Long ownerId, ConsignmentStatus filter, int page, int size) {
        var now = BusinessTime.now(); // 一次读取时间，用于筛选条件和结果状态。
        var query = Wrappers.lambdaQuery(ConsignmentEntity.class) // 构建可选的委托业务筛选。
                .eq(vehicleId != null, ConsignmentEntity::getVehicleId, vehicleId) // 车主查询限定指定车辆。
                .eq(ownerId != null, ConsignmentEntity::getOwnerId, ownerId); // 车主查询限定当前账号。
        if (filter == ConsignmentStatus.EXPIRED) {
            query.and(group -> group.eq(ConsignmentEntity::getStatus, ConsignmentStatus.EXPIRED) // 兼容已存储的到期状态。
                    .or(past -> past.in(ConsignmentEntity::getStatus, OPEN).le(ConsignmentEntity::getExpiresAt, now))); // 同时返回按业务时间自然失效的委托。
        } else if (filter != null) {
            query.eq(ConsignmentEntity::getStatus, filter) // 按指定持久化状态过滤。
                    .gt(OPEN.contains(filter), ConsignmentEntity::getExpiresAt, now); // 活动状态排除当前已到期的记录。
        }
        var entries = consignments.selectPage(BookingSupport.page(page, size), query.orderByDesc(ConsignmentEntity::getId)); // 稳定地按最新委托排序。
        var names = support.names(entries.getRecords().stream().map(ConsignmentEntity::getVehicleId).distinct().toList(),
                entries.getRecords().stream().map(ConsignmentEntity::getOwnerId).distinct().toList()); // 批量取得车源和车主名称。
        return BookingSupport.views(entries, row -> view(row, now, names)); // 分页返回脱离持久化实体的视图。
    }

    private View view(ConsignmentEntity row, LocalDateTime now) {
        return view(row, now, support.names(List.of(row.getVehicleId()), List.of(row.getOwnerId()))); // 单条响应复用相同的名称和状态展示。
    }

    private static View view(ConsignmentEntity row, LocalDateTime now, BookingSupport.Names names) {
        var state = status(row, now); // 将时间已结束的开放委托显示为已到期。
        boolean active = state == ConsignmentStatus.APPROVED && !row.getStartsAt().isAfter(now); // 只有审核通过且已开始的委托处于生效状态。
        return new View(row.getId(), row.getVehicleId(), names.vehicles().get(row.getVehicleId()),
                names.people().get(row.getOwnerId()), row.getStartsAt(), row.getExpiresAt(), row.getFee(), row.getTerms(),
                state, active, row.getReviewNote(), row.getReviewedAt(), row.getCreatedAt()); // 组装卖家和后台可以安全读取的字段。
    }

    private static ConsignmentStatus status(ConsignmentEntity row, LocalDateTime now) {
        return OPEN.contains(row.getStatus()) && !row.getExpiresAt().isAfter(now)
                ? ConsignmentStatus.EXPIRED : row.getStatus(); // 即使未执行定时任务，到期也会立即生效。
    }

    /** JSON 响应包含展示用名称，以及可单独解释的审核状态与生效状态。 */
    public record View(
            long id, // 委托编号。
            long vehicleId, // 所属车辆编号。
            String vehicleModel, // 车源车型名称。
            String ownerName, // 提交委托的卖家显示名称。
            LocalDateTime startsAt, // 委托起始时间。
            LocalDateTime expiresAt, // 委托截止时间。
            BigDecimal fee, // 代销费用。
            String terms, // 委托协议约定。
            ConsignmentStatus status, // 按当前业务时间计算后的状态。
            boolean active, // 标识已审核且生效的委托。
            String reviewNote, // 经办人员的审核说明。
            LocalDateTime reviewedAt, // 经办人员的审核时间。
            LocalDateTime createdAt // 卖家提交日期。
    ) { }
}
