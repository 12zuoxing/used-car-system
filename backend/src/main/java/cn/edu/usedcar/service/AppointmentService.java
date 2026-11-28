package cn.edu.usedcar.service; // 管理看车预约的业务规则。

import java.time.LocalDateTime; // 处理预约的北京时间。
import java.util.List; // 保存允许继续处理的预约状态。
import cn.edu.usedcar.common.ApiException; // 将越权和记录缺失映射为业务错误。
import cn.edu.usedcar.common.PageResult; // 返回分页数据结构。
import cn.edu.usedcar.dto.AppointmentHandlingRequest; // 接收工作人员选择的变更和备注。
import cn.edu.usedcar.dto.AppointmentRequest; // 接收买家填写的时间和备注。
import cn.edu.usedcar.entity.AppointmentEntity; // 读写预约表实体。
import cn.edu.usedcar.entity.VehicleEntity; // 使用锁定的车辆状态和发布人。
import cn.edu.usedcar.mapper.AppointmentMapper; // 执行预约持久化。
import cn.edu.usedcar.model.AppointmentStatus; // 管理预约状态。
import cn.edu.usedcar.model.BusinessTime; // 与代销和车源统一时区和时间精度。
import cn.edu.usedcar.model.VehicleStatus; // 判断车辆是否还在售。
import com.baomidou.mybatisplus.core.toolkit.Wrappers; // 组合安全的参数化查询。
import org.springframework.http.HttpStatus; // 使用匹配业务情况的 HTTP 状态。
import org.springframework.stereotype.Service; // 声明可注入的业务服务。
import org.springframework.transaction.annotation.Isolation; // 指定读事务快照和写事务可见性。
import org.springframework.transaction.annotation.Transactional; // 保持预约、日志和通知一致。

/** 看车预约的创建、查询、取消和后台处理。
 * @author pangzhihao
 */
@Service // 由 Spring 创建实例并代理事务。
public class AppointmentService {
    private static final List<AppointmentStatus> OPEN = List.of(AppointmentStatus.PENDING, AppointmentStatus.CONFIRMED); // 买家可取消的处理中状态。
    private final AppointmentMapper appointments; // 查询和写入预约。
    private final VehicleMediaAccess access; // 取得对应车辆的写锁。
    private final ConsignmentService consignments; // 检查代销车的当前委托是否有效。
    private final BookingSupport support; // 复用分页校验及列表显示名称。
    private final AuditLogService audit; // 保存预约处理轨迹。
    private final NotificationService notifications; // 按结果通知买家。

    public AppointmentService(AppointmentMapper appointments, VehicleMediaAccess access, ConsignmentService consignments,
                              BookingSupport support, AuditLogService audit, NotificationService notifications) {
        this.appointments = appointments; // 注入预约持久化接口。
        this.access = access; // 注入车源锁定组件。
        this.consignments = consignments; // 注入代销资格校验服务。
        this.support = support; // 注入分页辅助组件。
        this.audit = audit; // 注入审计服务。
        this.notifications = notifications; // 注入通知服务。
    }

    @Transactional(isolation = Isolation.READ_COMMITTED) // 按车辆行序列化提交，重复请求看到已保存的预约。
    public View create(long vehicleId, long buyerId, AppointmentRequest request) {
        var vehicle = access.lock(vehicleId); // 锁车源，防止提交时同时下架、撤销委托或进入订单。
        var now = BusinessTime.now(); // 在取得车辆锁后确定本次请求的时间边界。
        if (!bookable(vehicle, now)) throw vehicleUnavailable(); // 尚未上架或已失效的车源不能预约。
        if (vehicle.getOwnerId() == buyerId) throw BookingSupport.invalid("不能预约自己发布的车辆。"); // 确保买卖双方为两个账号。
        var visitAt = BusinessTime.toSecond(request.visitAt()); // 校验前对齐数据库小数秒精度。
        if (!visitAt.isAfter(now) || visitAt.getYear() > 9999) {
            throw BookingSupport.invalid("看车时间必须晚于当前时间。"); // 拒绝过去时间和数据库无法保存的值。
        }
        if (!consignments.isEligible(vehicle, visitAt)) {
            throw BookingSupport.invalid("看车时间需要在代销委托截止之前。"); // 防止预约落在委托失效后的时间。
        }
        var existing = appointments.selectCount(Wrappers.lambdaQuery(AppointmentEntity.class) // 查同一买家对同一车源的开放预约。
                .eq(AppointmentEntity::getVehicleId, vehicleId) // 限定申请所针对的车辆。
                .eq(AppointmentEntity::getBuyerId, buyerId) // 仅检查当前买家。
                .in(AppointmentEntity::getStatus, OPEN) // 已退回、取消或完成的记录保留历史。
                .gt(AppointmentEntity::getVisitAt, now)); // 已超过到访时间的旧预约不阻塞新请求。
        if (existing > 0) throw BookingSupport.conflict("APPOINTMENT_EXISTS", "已有待处理或已确认的看车预约。"); // 防止重复提交同一辆车。
        var row = new AppointmentEntity(); // 创建一条新的预约记录。
        row.setVehicleId(vehicleId); // 绑定经过校验的车辆。
        row.setBuyerId(buyerId); // 绑定会话中的买家身份。
        row.setVisitAt(visitAt); // 保存买家选择的到访时间。
        row.setNote(BookingSupport.note(request.note())); // 清理买家补充的可选备注。
        row.setStatus(AppointmentStatus.PENDING); // 等待工作人员登记处理结果。
        row.setCreatedAt(now); // 明确记录提交的业务时间。
        appointments.insert(row); // 插入记录并回填编号。
        audit.record(buyerId, "SUBMIT_APPOINTMENT", "APPOINTMENT", row.getId(), "提交看车预约"); // 写入提交事件的审计记录。
        return view(row, now); // 给买家返回可展示的预约结果。
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ) // 分页列表和计数使用同一数据库快照。
    public PageResult<View> mine(long buyerId, AppointmentStatus status, int page, int size) {
        return page(buyerId, status, page, size); // 限定为会话买家本人的预约。
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ) // 保证后台分页在并发更新期间仍然一致。
    public PageResult<View> queue(AppointmentStatus status, int page, int size) {
        return page(null, status, page, size); // 工作人员可查询跨买家的处理队列。
    }

    @Transactional(isolation = Isolation.READ_COMMITTED) // 获得车辆锁后重新查询预约的最新状态。
    public View cancel(long id, long buyerId) {
        access.lock(vehicleId(id)); // 先锁关联车辆，与后台经办串行处理。
        var row = required(id); // 锁定车辆后读取完整预约，避免覆盖工作人员刚执行的变更。
        if (row.getBuyerId() != buyerId) throw missing(); // 不向其他账号泄露预约是否存在。
        var now = BusinessTime.now(); // 以实际获得锁之后的时间判断是否可取消。
        if (!canCancel(row, now)) throw changed("仅未到访的待处理或已确认预约可以取消。"); // 避免取消已结束或已处理的记录。
        row.setStatus(AppointmentStatus.CANCELLED); // 保存买家主动取消状态。
        appointments.updateById(row); // 保留其他预约资料和已有经办说明。
        audit.record(buyerId, "CANCEL_APPOINTMENT", "APPOINTMENT", id, "买家取消看车预约"); // 写入取消操作的审计记录。
        return view(row, now); // 返回取消结果。
    }

    @Transactional(isolation = Isolation.READ_COMMITTED) // 车辆和预约状态在同一锁的保护下读取并更新。
    public View handle(long id, long handlerId, AppointmentHandlingRequest request) {
        var vehicle = access.lock(vehicleId(id)); // 先锁车辆，串行处理与提交、买家取消或车辆下架的竞争。
        var row = required(id); // 锁后重读预约，检查实际当前状态。
        var now = BusinessTime.now(); // 所有时间条件基于本次操作时刻。
        var next = request.status(); // 取后台明确选择的下一状态。
        var note = BookingSupport.note(request.note()); // 规范化工作人员填写的说明。
        switch (next) {
            case CONFIRMED -> {
                if (row.getStatus() != AppointmentStatus.PENDING || !row.getVisitAt().isAfter(now)) {
                    throw changed("仅到访时间未到的待处理预约可确认。"); // 避免确认已经过期或者处理过的预约。
                }
                if (!bookable(vehicle, now) || !consignments.isEligible(vehicle, row.getVisitAt())) {
                    throw changed("车源或代销期限已变化，可退回并说明原因。"); // 确认时再次验证车辆以及到访当日的代销资格。
                }
            }
            case REJECTED -> {
                if (row.getStatus() != AppointmentStatus.PENDING) throw changed("仅待处理预约可以退回。"); // 退回对应未确认的申请。
            }
            case CANCELLED -> {
                if (!OPEN.contains(row.getStatus())) throw changed("仅待处理或已确认的预约可取消。"); // 允许工作人员处理车源或到访安排变化。
            }
            case COMPLETED -> {
                if (row.getStatus() != AppointmentStatus.CONFIRMED || row.getVisitAt().isAfter(now)) {
                    throw changed("已确认且到访时间已到的预约才能标记完成。"); // 禁止提前完成或绕过确认。
                }
            }
            case PENDING -> throw BookingSupport.invalid("请选择可执行的处理结果。"); // 禁止将预约退回到初始状态。
        }
        if ((next == AppointmentStatus.REJECTED || next == AppointmentStatus.CANCELLED) && note == null) {
            throw BookingSupport.invalid("退回或取消预约时请填写原因。"); // 给买家保留可读的处理理由。
        }
        row.setStatus(next); // 设置已通过流转检查的新状态。
        row.setHandlerId(handlerId); // 保存执行本次操作的工作人员。
        row.setHandlingNote(note); // 保存给买家的说明，可为空。
        appointments.updateById(row); // 持久化状态和经办人。
        audit.record(handlerId, "HANDLE_APPOINTMENT", "APPOINTMENT", id, "预约处理结果：" + next); // 记录由谁进行了状态变更。
        var message = switch (next) {
            case CONFIRMED -> "看车预约已确认，请查看到访安排。"; // 通知买家按计划到访。
            case REJECTED -> "看车预约已退回，请查看原因。"; // 引导买家读取退回说明。
            case CANCELLED -> "工作人员取消了看车预约，请查看原因。"; // 告知买家安排已取消。
            case COMPLETED -> "看车预约已登记完成。"; // 告知买家看车环节已完成。
            case PENDING -> throw new IllegalStateException("预约不能回到初始状态"); // 状态校验已拒绝这个结果。
        };
        notifications.record(row.getBuyerId(), row.getVehicleId(), "appointment:" + id + ":" + next,
                "APPOINTMENT", id, message); // 在同一事务登记去重的处理结果通知。
        return view(row, now); // 返回工作人员刚保存的处理结果。
    }

    private boolean bookable(VehicleEntity vehicle, LocalDateTime now) {
        return vehicle.getStatus() == VehicleStatus.LISTED && consignments.isEligible(vehicle, now); // 与公开车源保持相同的在售和有效委托条件。
    }

    private static boolean canCancel(AppointmentEntity row, LocalDateTime now) {
        return OPEN.contains(row.getStatus()) && row.getVisitAt().isAfter(now); // 买家可在到访前取消进行中的预约。
    }

    private static ApiException vehicleUnavailable() {
        return new ApiException(HttpStatus.NOT_FOUND, "VEHICLE_NOT_FOUND", "该车源不存在或当前不可预约。"); // 公共入口隐藏未发布车辆。
    }

    private static ApiException missing() {
        return new ApiException(HttpStatus.NOT_FOUND, "APPOINTMENT_NOT_FOUND", "预约不存在或不可查看。"); // 用同一个错误表示缺失和无权读取。
    }

    private static ApiException changed(String text) {
        return BookingSupport.conflict("APPOINTMENT_STATE_CONFLICT", text); // 便于前端提示工作人员刷新数据。
    }

    private AppointmentEntity required(long id) {
        var row = appointments.selectById(id); // 根据业务主键取得预约记录。
        if (row == null) throw missing(); // 已不存在时停止处理。
        return row; // 将记录交给已完成车辆权限判断的调用路径。
    }

    private long vehicleId(long id) {
        var vehicleId = appointments.vehicleId(id); // 只查不可变外键，完整预约由锁后的读取获得。
        if (vehicleId == null) throw missing(); // 没有预约时不存在可锁定的车辆。
        return vehicleId; // 返回这个预约关联的车源编号。
    }

    private PageResult<View> page(Long buyerId, AppointmentStatus status, int page, int size) {
        var now = BusinessTime.now(); // 同一页使用统一的取消时间边界。
        var rows = appointments.selectPage(BookingSupport.page(page, size), Wrappers.lambdaQuery(AppointmentEntity.class)
                .eq(buyerId != null, AppointmentEntity::getBuyerId, buyerId) // 个人列表限定买家身份。
                .eq(status != null, AppointmentEntity::getStatus, status) // 接收可选的预约状态筛选。
                .orderByDesc(AppointmentEntity::getId)); // 按最新预约稳定排序。
        var names = support.names(rows.getRecords().stream().map(AppointmentEntity::getVehicleId).distinct().toList(),
                rows.getRecords().stream().map(AppointmentEntity::getBuyerId).distinct().toList()); // 分别批量读取车源和买家名称。
        return BookingSupport.views(rows, row -> view(row, now, names)); // 返回分页业务视图。
    }

    private View view(AppointmentEntity row, LocalDateTime now) {
        return view(row, now, support.names(List.of(row.getVehicleId()), List.of(row.getBuyerId()))); // 单条响应也提供展示用名称。
    }

    private static View view(AppointmentEntity row, LocalDateTime now, BookingSupport.Names names) {
        return new View(row.getId(), row.getVehicleId(), names.vehicles().get(row.getVehicleId()),
                names.people().get(row.getBuyerId()), row.getVisitAt(), row.getNote(), row.getStatus(),
                row.getHandlingNote(), row.getCreatedAt(), canCancel(row, now)); // 返回当前用户可读取的业务数据。
    }

    /** 面向预约参与人和后台工作的列表或单条视图。 */
    public record View(
            long id, // 预约编号。
            long vehicleId, // 对应车辆编号。
            String vehicleModel, // 对应车型名称。
            String buyerName, // 买家显示名称。
            LocalDateTime visitAt, // 预计到访时间。
            String note, // 买家填写的备注。
            AppointmentStatus status, // 预约当前处理结果。
            String handlingNote, // 工作人员给出的安排或原因。
            LocalDateTime createdAt, // 预约提交时间。
            boolean cancellable // 指示当前买家是否仍可取消。
    ) { }
}
