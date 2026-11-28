package cn.edu.usedcar.model; // 定义送修模块的两个业务阶段。

/** 工单阶段，与 repair_order 表的阶段约束一致。
 * @author pangzhihao
 */
public enum RepairStage {
    PRE_SALE, // 车源发布人为名下车辆申请的售前整备。
    AFTER_SALE // 原订单买家为已成交车辆申请的售后送修。
}
