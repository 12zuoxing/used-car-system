package cn.edu.usedcar.service; // 将贷款登记、补充与办理行为集中在业务层。

import java.math.BigDecimal; // 以精确小数比较订单与申请金额。
import java.time.LocalDateTime; // 保存和展示业务时间。
import java.util.List; // 组合详情涉及的车辆和参与人名称。
import java.util.function.Function; // 构建按订单编号索引的批量结果。
import java.util.stream.Collectors; // 整理分页涉及的车辆和订单关系。
import java.util.stream.Stream; // 提取申请人与经办人的编号。
import cn.edu.usedcar.common.ApiException; // 使用现有的 API 业务错误格式。
import cn.edu.usedcar.common.PageResult; // 使用统一分页结构。
import cn.edu.usedcar.dto.LoanRequest; // 保存申请或补充时填写的数据。
import cn.edu.usedcar.dto.LoanStatusRequest; // 读取工作人员的处理意图。
import cn.edu.usedcar.entity.LoanApplicationEntity; // 持久化贷款记录上的字段。
import cn.edu.usedcar.entity.TradeOrderEntity; // 从订单核对买家、金额和交易状态。
import cn.edu.usedcar.mapper.LoanMapper; // 查询和更新贷款申请。
import cn.edu.usedcar.mapper.TradeOrderMapper; // 从已有交易数据确定关联订单。
import cn.edu.usedcar.model.BusinessTime; // 复用项目的北京时间。
import cn.edu.usedcar.model.LoanStatus; // 控制申请办理流程。
import cn.edu.usedcar.model.TradeOrderStatus; // 阻止对取消订单继续办理贷款。
import com.baomidou.mybatisplus.core.toolkit.Wrappers; // 构造参数化的分页条件。
import org.springframework.http.HttpStatus; // 用适当的状态码表示冲突或缺失。
import org.springframework.stereotype.Service; // 将服务注册到应用容器。
import org.springframework.transaction.annotation.Isolation; // 指定车辆锁与只读分页的事务隔离。
import org.springframework.transaction.annotation.Propagation; // 让关联订单关闭贷款时复用外层事务。
import org.springframework.transaction.annotation.Transactional; // 确保保存、审计和通知同进同退。

/** 人工贷款登记服务。写操作统一先锁定订单对应的车辆。
 * @author pangzhihao
 */
@Service // 配合 Spring 的事务代理工作。
public class LoanService {
    private final LoanMapper loans; // 提供贷款表读写和版本查询。
    private final TradeOrderMapper orders; // 核对订单及参与人身份。
    private final VehicleMediaAccess vehicles; // 使用交易模块相同的车辆写锁。
    private final BookingSupport support; // 复用分页、文本处理和显示名称查询。
    private final AuditLogService audit; // 保存每次资料或状态变更的记录。
    private final NotificationService notifications; // 将办理结果登记为买家通知。

    public LoanService(LoanMapper loans, TradeOrderMapper orders, VehicleMediaAccess vehicles,
                       BookingSupport support, AuditLogService audit, NotificationService notifications) {
        this.loans = loans; // 注入贷款数据访问接口。
        this.orders = orders; // 注入订单数据访问接口。
        this.vehicles = vehicles; // 注入车源锁定组件。
        this.support = support; // 注入分页与名称工具。
        this.audit = audit; // 注入审核日志。
        this.notifications = notifications; // 注入通知登记服务。
    }

    @Transactional(isolation = Isolation.READ_COMMITTED) // 等到获取车辆锁后读取最新的订单和贷款记录。
    public Detail create(long orderId, long buyerId, LoanRequest form) {
        var order = lockedOrder(orderId); // 与这笔订单的取消动作使用同一个写锁。
        requireBuyer(order, buyerId); // 只有订单买家可登记贷款。
        requireOpenOrder(order); // 取消后的订单无法创建登记。
        if (loans.byOrder(orderId) != null) {
            throw BookingSupport.conflict("LOAN_ALREADY_EXISTS", "该订单已有贷款登记，请查看原申请。"); // 名额已被既有申请占用。
        }
        validateAmount(order, form); // 比较申请金额与真实的订单成交金额。
        var row = new LoanApplicationEntity(); // 创建待插入的数据。
        row.setOrderId(orderId); // 绑定经过校验的交易。
        row.setApplicantId(buyerId); // 使用会话中的买家身份。
        row.setStatus(LoanStatus.PENDING); // 新登记等待工作人员处理。
        row.setCreatedAt(BusinessTime.now()); // 保存初次申请时间。
        applyForm(row, form); // 写入金额、期数和说明。
        loans.insert(row); // 数据库唯一键继续保护一个订单只保留一份登记。
        audit.record(buyerId, "SUBMIT_LOAN", "LOAN", row.getId(), "提交贷款登记"); // 生成此轮申请的初始审核版本。
        return detailView(row, order); // 返回可直接显示的申请详情。
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ) // 订单身份与关联登记使用相同读取快照。
    public Detail forOrder(long orderId, long buyerId) {
        var order = requiredOrder(orderId); // 先检查这笔订单是否存在。
        requireBuyer(order, buyerId); // 即使还没有登记，也只允许买家查询。
        var row = loans.byOrder(orderId); // 此处最多返回一条申请。
        return row == null ? null : detailView(row, order); // 买家可区分尚未登记与已有历史。
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ) // 让申请、状态和审核版本保持一致。
    public Detail detail(long id, long buyerId) {
        var row = required(id); // 按独立的贷款申请编号定位详情。
        requireApplicant(row, buyerId); // 对非申请人统一隐藏资源。
        return detailView(row, requiredOrder(row.getOrderId())); // 返回本人申请的明细。
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ) // 防止查询中途状态和版本错配。
    public Detail staffDetail(long id) {
        var row = required(id); // 读取受后台角色保护的申请。
        return detailView(row, requiredOrder(row.getOrderId())); // 为工作人员提供同一个办理版本。
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ) // 总量、页面和名称使用同一快照。
    public PageResult<Summary> mine(long buyerId, LoanStatus status, int page, int pageSize) {
        return page(buyerId, status, page, pageSize); // 只筛选本人名下的申请。
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ) // 保持队列分页一致。
    public PageResult<Summary> queue(LoanStatus status, int page, int pageSize) {
        return page(null, status, page, pageSize); // 工作人员可以按状态查询经办队列。
    }

    @Transactional(isolation = Isolation.READ_COMMITTED) // 先获取车辆锁，再核对是否仍可补充资料。
    public Detail supplement(long id, long buyerId, Long revision, LoanRequest form) {
        var order = lockedOrder(orderId(id)); // 只从贷款读取不可变外键，再对订单关联车辆加锁。
        var row = required(id); // 车辆加锁后才读取完整申请状态。
        requireApplicant(row, buyerId); // 保证只有原申请人能重新提交。
        requireOpenOrder(order); // 关联订单仍存在有效业务办理资格。
        checkRevision(id, revision); // 带版本的客户端只能覆盖自己读取的版本。
        if (row.getStatus() != LoanStatus.SUPPLEMENT) throw stateConflict("仅待补充资料的申请可以修改。"); // 其他阶段保持材料稳定。
        validateAmount(order, form); // 补充后的新金额同样受原订单约束。
        applyForm(row, form); // 在原记录写入已修改的表单字段。
        row.setStatus(LoanStatus.PENDING); // 更新后的资料重新排队。
        loans.updateById(row); // 保留申请编号、首次提交时间和此前补充说明。
        audit.record(buyerId, "RESUBMIT_LOAN", "LOAN", id, "补充贷款资料并重新提交"); // 新一轮审核使用新的版本。
        return detailView(row, order); // 告知申请人补充成功。
    }

    @Transactional(isolation = Isolation.READ_COMMITTED) // 保证状态修改与并发提交或订单取消串行。
    public Detail handle(long id, long staffId, LoanStatusRequest request) {
        var order = lockedOrder(orderId(id)); // 使用与买家修改相同的车辆锁定顺序。
        var row = required(id); // 读取已提交的最新资料。
        requireOpenOrder(order); // 已取消交易中的申请不能继续经办。
        checkRevision(id, request.revision()); // 有新版补充资料时拒绝旧后台页面的操作。
        var next = request.status(); // 获取工作人员明确提交的下一状态。
        if (!canMove(row.getStatus(), next)) throw stateConflict("当前办理状态不允许此操作。"); // 结束状态与等待补充状态受到严格控制。
        var note = BookingSupport.note(request.resultNote()); // 将只有空格的说明视为未填写。
        if (next != LoanStatus.PROCESSING && note == null) {
            throw BookingSupport.invalid("退回补充、办理完成或关闭申请时须填写办理说明。"); // 让买家能理解结果和下一步。
        }
        row.setStatus(next); // 应用校验过的目标状态。
        row.setHandlerId(staffId); // 记录最近一次经办人的账号。
        row.setResultNote(note); // 保存本次办理说明。
        loans.updateById(row); // 在事务中更新经办数据。
        long revision = recordChange(row, order, staffId, "HANDLE_LOAN", "贷款状态：" + next); // 登记审计和通知，并取得最新办理版本。
        return detailView(row, order, revision); // 让后台可以用新版本继续合法的处理。
    }

    @Transactional(propagation = Propagation.MANDATORY) // 订单与贷款关闭必须在同一事务中提交。
    public void closeForCancelledOrder(TradeOrderEntity order, long actorId) {
        var row = loans.byOrder(order.getId()); // 调用方已经锁住车辆并登记了订单取消。
        if (row == null || row.getStatus() == LoanStatus.CANCELLED || row.getStatus() == LoanStatus.FINISHED) return; // 终结状态和无申请的订单保持原样。
        row.setStatus(LoanStatus.CANCELLED); // 关闭尚未完成的贷款登记。
        row.setResultNote("关联订单已取消。"); // 保留买家可读取的关闭原因。
        loans.updateById(row); // 持久化自动关闭结果。
        recordChange(row, order, actorId, "CLOSE_LOAN_WITH_ORDER", "订单取消后关闭贷款"); // 写入同一事务的审计和通知。
    }

    private long recordChange(LoanApplicationEntity row, TradeOrderEntity order, long actorId, String action, String detail) {
        audit.record(actorId, action, "LOAN", row.getId(), detail); // 审计使用动作和状态，不复制申请说明。
        long revision = loans.revision(row.getId()); // 获取刚追加的审计编号作为本轮版本。
        notifications.record(row.getApplicantId(), order.getVehicleId(), "loan:" + row.getId() + ":" + revision,
                "LOAN", row.getId(), message(row.getStatus())); // 事件以审计版本区分多次退回，并避免通知带出备注。
        return revision; // 让经办响应使用记录通知时的同一版本。
    }

    private static String message(LoanStatus status) {
        return switch (status) {
            case SUPPLEMENT -> "贷款登记需要补充资料，请到我的贷款查看。"; // 提醒买家填写补充说明。
            case PROCESSING -> "贷款登记已进入办理中。"; // 告知买家当前环节。
            case FINISHED -> "贷款登记已办理完成，请查看办理结果。"; // 将详情留在授权页面。
            case CANCELLED -> "贷款登记已关闭，请查看办理说明。"; // 说明当前不会继续处理。
            case PENDING -> throw new IllegalStateException("待处理申请不发送经办通知"); // 买家自行提交或补充时不调用通知流程。
        };
    }

    private static boolean canMove(LoanStatus current, LoanStatus next) {
        return switch (current) {
            case PENDING -> next == LoanStatus.SUPPLEMENT || next == LoanStatus.PROCESSING || next == LoanStatus.CANCELLED; // 初审可退回、接办或关闭。
            case PROCESSING -> next == LoanStatus.SUPPLEMENT || next == LoanStatus.FINISHED || next == LoanStatus.CANCELLED; // 办理中可补件、完成或关闭。
            case SUPPLEMENT -> next == LoanStatus.CANCELLED; // 补充后的恢复待审只能由申请人发起。
            case FINISHED, CANCELLED -> false; // 终局状态保留历史结果。
        };
    }

    private PageResult<Summary> page(Long buyerId, LoanStatus status, int page, int pageSize) {
        var filter = Wrappers.lambdaQuery(LoanApplicationEntity.class)
                .eq(buyerId != null, LoanApplicationEntity::getApplicantId, buyerId) // 个人页只包含本会话的申请。
                .eq(status != null, LoanApplicationEntity::getStatus, status) // 提供可选的状态过滤。
                .orderByDesc(LoanApplicationEntity::getId); // 用创建顺序保证分页稳定。
        var rows = loans.selectPage(BookingSupport.page(page, pageSize), filter); // 在数据库中计算总量和本页记录。
        if (rows.getRecords().isEmpty()) return new PageResult<>(List.of(), rows.getTotal(), page, pageSize); // 避免空集合发起关联查询。
        var orderIds = rows.getRecords().stream().map(LoanApplicationEntity::getOrderId).toList(); // 每个贷款记录关联一个不同的订单。
        var orderRows = orders.selectByIds(orderIds).stream()
                .collect(Collectors.toMap(TradeOrderEntity::getId, Function.identity())); // 一次读取本页涉及的订单。
        var names = support.names(orderRows.values().stream().map(TradeOrderEntity::getVehicleId).distinct().toList(),
                rows.getRecords().stream().map(LoanApplicationEntity::getApplicantId).distinct().toList()); // 一次收集车源标题和申请人名称。
        return BookingSupport.views(rows, row -> summary(row, orderRows.get(row.getOrderId()), names)); // 列表只返回识别记录和进度需要的字段。
    }

    private static Summary summary(LoanApplicationEntity row, TradeOrderEntity order, BookingSupport.Names names) {
        return new Summary(row.getId(), order.getId(), names.vehicles().get(order.getVehicleId()),
                names.people().get(row.getApplicantId()), row.getAmount(), row.getMonths(), row.getStatus(), row.getCreatedAt()); // 组装授权列表的摘要。
    }

    private Detail detailView(LoanApplicationEntity row, TradeOrderEntity order) {
        return detailView(row, order, loans.revision(row.getId())); // 首次读取或修改后使用当前审计版本。
    }

    private Detail detailView(LoanApplicationEntity row, TradeOrderEntity order, long revision) {
        var people = Stream.of(row.getApplicantId(), row.getHandlerId()).filter(java.util.Objects::nonNull).distinct().toList(); // 区分已有经办人与尚未经办。
        var names = support.names(List.of(order.getVehicleId()), people); // 一次取得详情展示所需的名称。
        var item = summary(row, order, names); // 详情与摘要共用车型和金额表达。
        return new Detail(item.id(), item.orderId(), item.vehicleModel(), item.applicantName(),
                row.getHandlerId() == null ? null : names.people().get(row.getHandlerId()), order.getAmount(), item.amount(),
                item.months(), row.getNote(), item.status(), row.getResultNote(), item.createdAt(), revision); // 详细备注只出现在已授权的详情中。
    }

    private void checkRevision(long id, Long revision) {
        if (revision != null && revision < 0) throw BookingSupport.invalid("贷款版本格式不正确。"); // 负数不是合法的审计版本。
        if (revision != null && loans.revision(id) != revision) {
            throw BookingSupport.conflict("STALE_LOAN", "贷款资料已更新，请刷新后再提交。"); // 防止上一轮资料的审核复用到新一轮。
        }
    }

    private TradeOrderEntity lockedOrder(long id) {
        var vehicleId = orders.vehicleId(id); // 先取不随交易状态改变的车辆外键。
        if (vehicleId == null) throw orderMissing(); // 未找到关联订单时停止。
        vehicles.lock(vehicleId); // 与下单、取消等交易操作共享车辆写锁。
        return requiredOrder(id); // 加锁后读取实际订单状态和金额。
    }

    private TradeOrderEntity requiredOrder(long id) {
        var order = orders.selectById(id); // 读取订单的已存价格与买家身份。
        if (order == null) throw orderMissing(); // 隐藏不可见的订单。
        return order; // 将已找到的订单交给业务校验。
    }

    private LoanApplicationEntity required(long id) {
        var row = loans.selectById(id); // 根据贷款主键读取登记资料。
        if (row == null) throw missing(); // 没有对应记录时返回缺失。
        return row; // 返回当前事务内读到的状态。
    }

    private long orderId(long id) {
        var orderId = loans.orderId(id); // 锁车前仅查稳定的申请外键。
        if (orderId == null) throw missing(); // 找不到时无需获取车源锁。
        return orderId; // 保持所有写操作的统一锁定顺序。
    }

    private static void requireBuyer(TradeOrderEntity order, long buyerId) {
        if (order.getBuyerId() != buyerId) throw orderMissing(); // 不能从贷款路径识别他人的订单。
    }

    private static void requireApplicant(LoanApplicationEntity row, long buyerId) {
        if (row.getApplicantId() != buyerId) throw missing(); // 只有本人能看到或补充私有申请。
    }

    private static void requireOpenOrder(TradeOrderEntity order) {
        if (order.getStatus() == TradeOrderStatus.CANCELLED) throw stateConflict("关联订单已取消，无法继续办理贷款登记。"); // 取消交易后不允许新的贷款动作。
    }

    private static void validateAmount(TradeOrderEntity order, LoanRequest form) {
        if (form.amount().compareTo(order.getAmount()) > 0) {
            throw BookingSupport.invalid("申请金额不能超过订单金额。"); // 只信任服务端保存的订单金额。
        }
    }

    private static void applyForm(LoanApplicationEntity row, LoanRequest form) {
        row.setAmount(form.amount()); // 保存买家申请的金额。
        row.setMonths(form.months()); // 保存经参数校验的贷款期限。
        row.setNote(BookingSupport.note(form.note())); // 统一整理可选的说明内容。
    }

    private static ApiException missing() {
        return new ApiException(HttpStatus.NOT_FOUND, "LOAN_NOT_FOUND", "贷款登记不存在或不可查看。"); // 缺失和无权查看使用相同响应。
    }

    private static ApiException orderMissing() {
        return new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "订单不存在或不可查看。"); // 不对外暴露订单实际归属。
    }

    private static ApiException stateConflict(String message) {
        return BookingSupport.conflict("LOAN_STATE_CONFLICT", message); // 明确标识状态冲突。
    }

    /** 个人和经办列表使用的贷款摘要。 */
    public record Summary(
            long id, // 贷款登记编号。
            long orderId, // 申请绑定的交易单号。
            String vehicleModel, // 关联车辆显示名称。
            String applicantName, // 申请人显示名称。
            BigDecimal amount, // 申请金额。
            int months, // 贷款期数。
            LoanStatus status, // 最新办理状态。
            LocalDateTime createdAt // 申请首次创建时间。
    ) { }

    /** 经过授权的贷款详情及用于审核的版本。 */
    public record Detail(
            long id, // 原登记编号，补充后继续使用。
            long orderId, // 关联订单号。
            String vehicleModel, // 便于识别订单的车型。
            String applicantName, // 提交申请的买家。
            String handlerName, // 最近的工作人员显示名称。
            BigDecimal orderAmount, // 条件校验使用的成交金额。
            BigDecimal amount, // 买家提交的贷款金额。
            int months, // 买家提交的期数。
            String note, // 买家登记的说明。
            LoanStatus status, // 当前阶段。
            String resultNote, // 工作人员要求或经办结果。
            LocalDateTime createdAt, // 初次提交时刻。
            long revision // 以最近一次贷款审计编号指示当前版本。
    ) { }
}
