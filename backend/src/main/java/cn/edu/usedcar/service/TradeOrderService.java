package cn.edu.usedcar.service; // 将下单、支付、取消和过户规则集中在服务层。

import java.math.BigDecimal; // 以十进制定点数返回订单金额。
import java.time.LocalDateTime; // 记录各个交易步骤的业务时间。
import java.util.List; // 为单条或分页视图整理展示名称。
import java.util.Locale; // 在任何部署语言下使用一致的请求标识大小写。
import java.util.Set; // 在读取姓名时去除重复的参与人编号。
import java.util.regex.Pattern; // 校验幂等请求标识的格式。
import java.util.stream.Stream; // 批量聚合当前页的买家和卖家编号。
import cn.edu.usedcar.common.ApiException; // 通过项目统一机制返回业务错误。
import cn.edu.usedcar.common.PageResult; // 复用项目的分页响应。
import cn.edu.usedcar.entity.TradeOrderEntity; // 持久化订单和交易状态。
import cn.edu.usedcar.entity.VehicleEntity; // 读取锁定车源的售价、发布者和审核结果。
import cn.edu.usedcar.entity.VehicleLockEntity; // 记录当前占用这辆车的订单。
import cn.edu.usedcar.mapper.TradeOrderMapper; // 查询和更新订单。
import cn.edu.usedcar.mapper.VehicleLockMapper; // 成对校验并释放车辆锁。
import cn.edu.usedcar.mapper.VehicleMapper; // 按预期状态修改车源。
import cn.edu.usedcar.model.BusinessTime; // 复用北京时间和数据库秒级边界。
import cn.edu.usedcar.model.OrderSide; // 控制个人中心的买入和卖出筛选。
import cn.edu.usedcar.model.SaleMode; // 区分直销和代销的成交结案规则。
import cn.edu.usedcar.model.TradeOrderStatus; // 定义允许的交易状态。
import cn.edu.usedcar.model.VehicleStatus; // 解释当前车辆是否可以成交。
import com.baomidou.mybatisplus.core.toolkit.Wrappers; // 构建带参数的分页筛选。
import org.springframework.dao.DuplicateKeyException; // 在并发标识冲突时回滚本次预订。
import org.springframework.http.HttpStatus; // 用 HTTP 状态表示缺失、越权或业务冲突。
import org.springframework.stereotype.Service; // 由 Spring 管理业务服务。
import org.springframework.transaction.PlatformTransactionManager; // 建立显式的下单提交边界。
import org.springframework.transaction.TransactionDefinition; // 设置下单独立事务的传播和隔离策略。
import org.springframework.transaction.annotation.Isolation; // 对不同业务场景使用相应隔离级别。
import org.springframework.transaction.annotation.Transactional; // 保证变更与通知一起提交。
import org.springframework.transaction.support.TransactionTemplate; // 在事务结束后处理唯一键竞争。

/** 订单以车辆为写入锁的入口，并由数据库唯一约束保护幂等键。
 * @author pangzhihao
 */
@Service // 让控制器只做参数绑定，由本类统一执行业务。
public class TradeOrderService {
    private static final Pattern REQUEST_KEY = Pattern.compile("[A-Za-z0-9._:-]{1,64}"); // 同时接受 UUID 和普通业务请求标识。
    private final TradeOrderMapper orders; // 管理订单历史。
    private final VehicleLockMapper locks; // 管理每辆车的唯一交易锁。
    private final VehicleMapper vehicles; // 更新锁定、取消和成交后的车源状态。
    private final VehicleMediaAccess access; // 与撤回委托及车源审核共享车辆行锁。
    private final ConsignmentService consignments; // 验证并结案代销委托。
    private final BookingSupport support; // 复用列表分页、查名及错误处理。
    private final AuditLogService audit; // 为关键交易操作写入责任记录。
    private final NotificationService notifications; // 在同一事务登记买卖双方应收到的状态通知。
    private final LoanService loans; // 订单取消时同步关闭尚未结案的贷款登记。
    private final TransactionTemplate createTransaction; // 幂等键竞争失败后允许在事务外读取赢家订单。

    public TradeOrderService(TradeOrderMapper orders, VehicleLockMapper locks, VehicleMapper vehicles,
                             VehicleMediaAccess access, ConsignmentService consignments, BookingSupport support,
                             AuditLogService audit, NotificationService notifications, PlatformTransactionManager transactions,
                             LoanService loans) {
        this.orders = orders; // 注入订单持久层。
        this.locks = locks; // 注入车辆锁持久层。
        this.vehicles = vehicles; // 注入车源持久层。
        this.access = access; // 注入现有车源加锁组件。
        this.consignments = consignments; // 注入代销资格服务。
        this.support = support; // 注入通用分页展示支持。
        this.audit = audit; // 注入操作记录服务。
        this.notifications = notifications; // 注入通知记录服务。
        this.loans = loans; // 注入关联登记处理服务。
        this.createTransaction = new TransactionTemplate(transactions); // 明确由本类管理下单事务的开始和结束。
        this.createTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW); // 让失败的预订完整回滚后再做幂等恢复。
        this.createTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED); // 拿到车源锁后读取其他请求已经提交的订单。
    }

    public Creation create(long vehicleId, long buyerId, String requestKey) {
        String key = normalize(requestKey); // 统一请求标识格式和大小写，避免数据库排序规则造成差异。
        var existing = orders.findByRequest(buyerId, key); // 优先返回曾经创建的订单，车源即使已不可售也可安全重试。
        if (existing != null) return new Creation(view(sameRequest(existing, vehicleId), buyerId), false); // 复用原订单时不用重复锁车。
        Reservation reservation; // 保存事务内确定的交易结果。
        try {
            reservation = createTransaction.execute(ignored -> reserve(vehicleId, buyerId, key)); // 在独立事务内完成抢占和持久化。
        } catch (DuplicateKeyException duplicate) {
            var winner = orders.findByRequest(buyerId, key); // 唯一键失败时，事务已回滚，所以可以读取已经提交的胜者。
            if (winner == null) throw lockConflict(); // 没有可复用的订单时，车辆已有不一致的锁，拒绝创建。
            reservation = new Reservation(sameRequest(winner, vehicleId), false); // 跨车复用会返回冲突，同车可恢复原订单。
        }
        return new Creation(view(reservation.order(), buyerId), reservation.fresh()); // 在事务提交后补充视图名称，缩短写锁时间。
    }

    private Reservation reserve(long vehicleId, long buyerId, String key) {
        var vehicle = access.lock(vehicleId); // 所有车源写入按同一行锁排队，后续资格校验基于当前状态。
        var existing = orders.findByRequest(buyerId, key); // 等待车源锁期间，同车的相同下单请求可能已提交。
        if (existing != null) return new Reservation(sameRequest(existing, vehicleId), false); // 同一辆车的幂等重试也只生成一个订单。
        if (vehicle.getOwnerId() == buyerId) throw BookingSupport.invalid("不能购买自己发布的车辆。"); // 保证买卖双方是不同账号。
        var now = BusinessTime.now(); // 取得车辆锁后统一计算价格保存时间和委托有效期。
        Long consignmentId = consignments.requireForOrder(vehicle, now); // 在当前车辆锁的保护下鉴别可售状态和当前有效委托。
        transition(vehicle, VehicleStatus.LISTED, VehicleStatus.LOCKED, vehicle.getListedAt()); // 只有当前在售的车源才能进入锁定状态。
        var order = new TradeOrderEntity(); // 创建订单持久化记录。
        order.setVehicleId(vehicleId); // 绑定已锁定的车源。
        order.setBuyerId(buyerId); // 买家只能来自登录会话。
        order.setSellerId(vehicle.getOwnerId()); // 卖家从服务端车辆记录获得。
        order.setConsignmentId(consignmentId); // 保存代销交易采用的原始委托编号。
        order.setRequestKey(key); // 交由数据库唯一键约束同一买家的同次请求。
        order.setAmount(vehicle.getPrice()); // 以车源锁定当时的服务端价格生成订单。
        order.setStatus(TradeOrderStatus.LOCKED); // 等待买家发起模拟支付。
        order.setCreatedAt(now); // 写入锁定车辆时的业务时间。
        orders.insert(order); // 插入并回填订单编号。
        var lock = new VehicleLockEntity(); // 为这笔订单创建车辆占用记录。
        lock.setVehicleId(vehicleId); // 车源编号作为主键确保一车只有一把锁。
        lock.setOrderId(order.getId()); // 指明锁由当前订单持有。
        lock.setLockedAt(now); // 保存锁定时间。
        locks.insert(lock); // 锁插入失败会让已写入的订单和车源状态一起回滚。
        audit.record(buyerId, "CREATE_ORDER", "ORDER", order.getId(), "创建订单并锁定车源"); // 保存下单人和锁车审计。
        notify(order.getSellerId(), order, "created", "你的车源已被下单，等待买家模拟支付。"); // 告知卖家锁车原因。
        return new Reservation(order, true); // 标记这是首次成功下单。
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ) // 分页和展示查询共享同一数据库快照。
    public PageResult<View> mine(long accountId, OrderSide side, TradeOrderStatus status, int page, int pageSize) {
        return page(accountId, side, status, page, pageSize); // 以下单或出售角色查询本人的订单。
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ) // 管理队列统一计算总数和分页结果。
    public PageResult<View> queue(TradeOrderStatus status, int page, int pageSize) {
        return page(null, OrderSide.ALL, status, page, pageSize); // 允许工作人员按状态查询所有订单。
    }

    @Transactional(readOnly = true) // 读取订单记录与关联名称。
    public View detail(long id, long accountId) {
        var order = required(id); // 根据路径编号查询订单。
        requireParticipant(order, accountId); // 无关账号收到与订单缺失一致的响应。
        return view(order, accountId); // 返回针对当前交易角色的详情。
    }

    @Transactional(readOnly = true) // 后台详情读取与公开资料隔离。
    public View staffDetail(long id) {
        return view(required(id), null); // 后台详情由角色保护，响应不启用买家和卖家动作。
    }

    @Transactional(isolation = Isolation.READ_COMMITTED) // 下单、取消和模拟支付按车辆锁串行执行。
    public View cancel(long id, long actorId) {
        var vehicle = lockVehicleForOrder(id); // 先锁车再读取订单的最新状态。
        var order = required(id); // 在车辆锁内获得当前的订单。
        requireParticipant(order, actorId); // 只有交易双方可以取消未支付订单。
        if (order.getStatus() != TradeOrderStatus.LOCKED) throw stateConflict("仅待支付订单可以取消。"); // 旧单与已付款订单不可触碰车辆锁。
        finishCancellation(vehicle, order, actorId); // 在事务中释放车辆并更新订单和关联贷款。
        audit.record(actorId, "CANCEL_ORDER", "ORDER", id, "买卖双方在支付前取消订单"); // 区分负责取消的人和系统状态。
        long other = order.getBuyerId() == actorId ? order.getSellerId() : order.getBuyerId(); // 找到取消动作的另一位交易参与人。
        notify(other, order, "cancelled", "本笔待支付订单已取消。"); // 向另一方登记取消消息。
        return view(order, actorId); // 给操作人返回最新订单状态。
    }

    @Transactional(isolation = Isolation.READ_COMMITTED) // 后台取消和确认过户通过同一车辆锁串行。
    public View cancelPaid(long id, long staffId, String note) {
        String reason = BookingSupport.note(note); // 去除原因两端的空白后再保存。
        if (reason == null) throw BookingSupport.invalid("请填写取消原因。"); // 业务入口也要求填写原因。
        var vehicle = lockVehicleForOrder(id); // 先锁定车源，使取消和成交互斥。
        var order = required(id); // 等待车辆锁后读取订单的最新状态。
        if (order.getStatus() != TradeOrderStatus.PAID) throw stateConflict("仅已支付且待过户的订单可由工作人员取消。");
        order.setHandlingNote(reason); // 保存双方可在详情页看到的原因，同时保留模拟支付时间。
        finishCancellation(vehicle, order, staffId); // 释放本订单持有的车源并关闭未完成的贷款。
        audit.record(staffId, "CANCEL_PAID_ORDER", "ORDER", id, "工作人员取消已模拟支付订单"); // 记录操作人和变更类型。
        String message = "工作人员已取消本笔订单，请在订单详情查看原因。"; // 双方收到相同提示，取消原因由有权访问详情的人员查看。
        notify(order.getBuyerId(), order, "cancelled", message); // 向买家登记取消通知。
        notify(order.getSellerId(), order, "cancelled", message); // 向卖家登记取消通知。
        return view(order, null); // 返回后台可见的订单处理结果。
    }

    private void finishCancellation(VehicleEntity vehicle, TradeOrderEntity order, long actorId) {
        requireCurrentLock(vehicle, order); // 检验指定订单仍持有这辆车。
        boolean relist = vehicle.getListedAt() != null && vehicle.getReviewedAt() != null && vehicle.getReviewerId() != null
                && consignments.isEligible(vehicle, BusinessTime.now()); // 只有仍符合审核与代销条件的车辆才能恢复在售。
        release(order); // 按车辆和订单两个编号执行释放。
        transition(vehicle, VehicleStatus.LOCKED, relist ? VehicleStatus.LISTED : VehicleStatus.OFFLINE,
                relist ? vehicle.getListedAt() : null); // 保留仍有效的原上架时间，失效车源重置为下架。
        order.setStatus(TradeOrderStatus.CANCELLED); // 保留已取消的订单历史。
        orders.updateById(order); // 持久化订单状态。
        loans.closeForCancelledOrder(order, actorId); // 复用车源写锁和取消事务关闭未完成贷款。
    }

    @Transactional(isolation = Isolation.READ_COMMITTED) // 付款与取消通过同一车辆锁串行。
    public View pay(long id, long buyerId) {
        var vehicle = lockVehicleForOrder(id); // 锁车后核对支付所对应的订单。
        var order = required(id); // 在车锁内读取订单最新状态。
        requireParticipant(order, buyerId); // 拒绝无关账号并隐藏记录是否存在。
        if (order.getBuyerId() != buyerId) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "仅买家可以模拟支付。"); // 卖家可查看进度，但无法替买家付款。
        }
        if (order.getStatus() == TradeOrderStatus.COMPLETED) return view(order, buyerId); // 成交后重发支付请求时返回已完成结果。
        if (order.getStatus() != TradeOrderStatus.LOCKED && order.getStatus() != TradeOrderStatus.PAID) {
            throw stateConflict("当前订单无法模拟支付。"); // 取消后的订单不能重新支付。
        }
        requireCurrentLock(vehicle, order); // 避免支付已经失去车辆占用的订单。
        if (order.getStatus() == TradeOrderStatus.PAID) return view(order, buyerId); // 保留首次付款时间并避免重复通知。
        order.setStatus(TradeOrderStatus.PAID); // 推进到等待工作人员确认成交。
        order.setPaidAt(BusinessTime.now()); // 写入系统的模拟支付时刻。
        orders.updateById(order); // 同时保存支付状态及时间。
        audit.record(buyerId, "PAY_ORDER", "ORDER", id, "买家完成模拟支付"); // 记录完成模拟支付的人。
        notify(order.getBuyerId(), order, "paid", "模拟支付成功，等待工作人员确认过户。"); // 让买家知道后续进度。
        notify(order.getSellerId(), order, "paid", "买家已完成模拟支付，等待过户确认。"); // 让卖家知道可以配合办理过户。
        return view(order, buyerId); // 返回已支付的订单详情。
    }

    @Transactional(isolation = Isolation.READ_COMMITTED) // 任一成交写入失败会保留此前的订单和车辆占用。
    public View complete(long id, long staffId, String note) {
        var vehicle = lockVehicleForOrder(id); // 与取消和支付按相同顺序加锁。
        var order = required(id); // 取得当前真实的支付状态。
        if (order.getStatus() != TradeOrderStatus.PAID) throw stateConflict("仅已支付订单可确认成交。"); // 必须先由买家完成模拟支付。
        requireCurrentLock(vehicle, order); // 确认即将成交的订单仍是当前的车辆锁持有者。
        if (vehicle.getSaleMode() == SaleMode.CONSIGN) {
            consignments.completeForOrder(vehicle, order.getConsignmentId()); // 对有效期内创建的代销订单结案其原始委托。
        }
        release(order); // 仅释放当前成交订单持有的车辆锁。
        transition(vehicle, VehicleStatus.LOCKED, VehicleStatus.SOLD, vehicle.getListedAt()); // 成交后车辆同步标记为已售。
        order.setStatus(TradeOrderStatus.COMPLETED); // 保存确认后的终态。
        order.setCompletedAt(BusinessTime.now()); // 记录工作人员确认过户的时间。
        order.setHandlingNote(BookingSupport.note(note)); // 清理并保存可选的过户说明。
        orders.updateById(order); // 持久化成交状态、说明和时间。
        audit.record(staffId, "COMPLETE_ORDER", "ORDER", id, "工作人员确认过户成交"); // 保留处理过户的审核轨迹。
        notify(order.getBuyerId(), order, "completed", "工作人员已确认过户，交易已完成。"); // 通知买家成交。
        notify(order.getSellerId(), order, "completed", "工作人员已确认过户，交易已完成。"); // 通知卖家成交。
        return view(order, null); // 返回后台视角的完整成交资料。
    }

    private PageResult<View> page(Long accountId, OrderSide side, TradeOrderStatus status, int page, int pageSize) {
        var query = Wrappers.lambdaQuery(TradeOrderEntity.class).eq(status != null, TradeOrderEntity::getStatus, status); // 只在指定状态时应用过滤。
        if (accountId != null) {
            switch (side) {
                case BUY -> query.eq(TradeOrderEntity::getBuyerId, accountId); // 仅返回当前买家的买入历史。
                case SELL -> query.eq(TradeOrderEntity::getSellerId, accountId); // 仅返回当前卖家的出售历史。
                case ALL -> query.and(owner -> owner.eq(TradeOrderEntity::getBuyerId, accountId)
                        .or().eq(TradeOrderEntity::getSellerId, accountId)); // 个人完整列表依然只包含自己的交易。
            }
        }
        var result = orders.selectPage(BookingSupport.page(page, pageSize), query.orderByDesc(TradeOrderEntity::getId)); // 由 MyBatis-Plus 完成分页和按最新编号排序。
        var names = support.names(result.getRecords().stream().map(TradeOrderEntity::getVehicleId).distinct().toList(),
                result.getRecords().stream().flatMap(row -> Stream.of(row.getBuyerId(), row.getSellerId())).distinct().toList()); // 批量读取车型与参与人，避免逐笔查找。
        return BookingSupport.views(result, row -> view(row, accountId, names)); // 保留分页元数据并返回授权视图。
    }

    private View view(TradeOrderEntity order, Long actorId) {
        var names = support.names(List.of(order.getVehicleId()), Set.of(order.getBuyerId(), order.getSellerId())); // 为单条订单加载必要展示字段。
        return view(order, actorId, names); // 与列表共用同样的显示规则。
    }

    private static View view(TradeOrderEntity order, Long actorId, BookingSupport.Names names) {
        OrderSide side = actorId == null ? null : order.getBuyerId().equals(actorId) ? OrderSide.BUY : OrderSide.SELL; // 区分个人交易角色和后台视角。
        return new View(order.getId(), order.getVehicleId(), names.vehicles().get(order.getVehicleId()),
                names.people().get(order.getBuyerId()), names.people().get(order.getSellerId()), order.getConsignmentId(),
                order.getAmount(), order.getStatus(), side, order.getHandlingNote(), order.getCreatedAt(),
                order.getPaidAt(), order.getCompletedAt()); // 仅返回角色授权后的业务字段，幂等标识不进入视图。
    }

    private VehicleEntity lockVehicleForOrder(long id) {
        var vehicleId = orders.vehicleId(id); // 只读取不可变的外键，避免锁车前缓存旧订单状态。
        if (vehicleId == null) throw missing(); // 缺失订单没有对应的锁定目标。
        return access.lock(vehicleId); // 阻止另一个事务同时修改这笔订单所属的车辆。
    }

    private TradeOrderEntity required(long id) {
        var order = orders.selectById(id); // 使用主键读取订单。
        if (order == null) throw missing(); // 用统一的错误响应订单不可访问。
        return order; // 返回在当前读取上下文中的订单。
    }

    private static void requireParticipant(TradeOrderEntity order, long actorId) {
        if (order.getBuyerId() != actorId && order.getSellerId() != actorId) throw missing(); // 只让买家和卖家知道这笔个人订单。
    }

    private static TradeOrderEntity sameRequest(TradeOrderEntity order, long vehicleId) {
        if (order.getVehicleId() != vehicleId) {
            throw BookingSupport.conflict("IDEMPOTENCY_KEY_REUSED", "该请求标识已用于另一辆车。"); // 一个请求标识始终绑定原始车辆。
        }
        return order; // 同一车辆可直接复用先前提交的交易。
    }

    private static String normalize(String key) {
        if (key == null || !REQUEST_KEY.matcher(key).matches()) {
            throw BookingSupport.invalid("下单请求需要 1 至 64 位有效的 Idempotency-Key。"); // 无标识或超出限定字符范围时拒绝下单。
        }
        return key.toLowerCase(Locale.ROOT); // 让 H2 和 MySQL 都按同一规则识别请求标识。
    }

    private void requireCurrentLock(VehicleEntity vehicle, TradeOrderEntity order) {
        var current = locks.selectById(vehicle.getId()); // 用车源编号查询目前的占用者。
        if (vehicle.getStatus() != VehicleStatus.LOCKED || current == null || !current.getOrderId().equals(order.getId())) {
            throw lockConflict(); // 车辆状态、车源锁和订单必须相互一致。
        }
    }

    private void release(TradeOrderEntity order) {
        if (locks.release(order.getVehicleId(), order.getId()) != 1) throw lockConflict(); // 双条件删除保证不会解锁别人的订单。
    }

    private void transition(VehicleEntity vehicle, VehicleStatus expected, VehicleStatus next, LocalDateTime listedAt) {
        if (vehicles.transitionOrderStatus(vehicle.getId(), expected, next, listedAt) != 1) {
            throw BookingSupport.conflict("VEHICLE_STATE_CONFLICT", "车源状态已变化，请刷新后再操作。"); // 车源状态写入必须匹配当前预期。
        }
    }

    private void notify(long receiver, TradeOrderEntity order, String event, String message) {
        notifications.record(receiver, order.getVehicleId(), "order:" + order.getId() + ":" + event,
                "ORDER", order.getId(), message); // 使用同一订单和动作标识去重，记录和业务事务一起提交。
    }

    private static ApiException missing() {
        return new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "订单不存在或不可查看。"); // 避免向非交易方泄露订单。
    }

    private static ApiException stateConflict(String message) {
        return BookingSupport.conflict("ORDER_STATE_CONFLICT", message); // 用统一的状态冲突标识帮助界面刷新。
    }

    private static ApiException lockConflict() {
        return BookingSupport.conflict("ORDER_LOCK_CONFLICT", "车辆占用与订单不一致，请刷新后再处理。"); // 捕获不安全的释放或成交。
    }

    private record Reservation(TradeOrderEntity order, boolean fresh) { } // 保存事务内部决定的订单与是否首次创建。
    public record Creation(View order, boolean fresh) { } // 帮助控制器区分创建成功与幂等重放。

    /** 个人和后台的安全订单视图。 */
    public record View(
            long id, // 交易订单编号。
            long vehicleId, // 订单所购车辆编号。
            String vehicleModel, // 方便识别的车源车型名称。
            String buyerName, // 买方显示名称。
            String sellerName, // 卖方显示名称。
            Long consignmentId, // 代销依据，直销为空。
            BigDecimal amount, // 下单时保存的成交金额。
            TradeOrderStatus status, // 当前的交易状态。
            OrderSide side, // 当前访问者作为买家还是卖家。
            String handlingNote, // 工作人员填写的过户说明或取消原因。
            LocalDateTime createdAt, // 订单提交时间。
            LocalDateTime paidAt, // 模拟支付成功时间。
            LocalDateTime completedAt // 工作人员确认成交时间。
    ) { }
}
