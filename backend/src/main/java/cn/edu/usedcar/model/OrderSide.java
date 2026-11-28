package cn.edu.usedcar.model; // 为个人中心提供固定的订单查询角色。

/** 一名用户可以作为买家或卖家筛选交易。
 * @author pangzhihao
 */
public enum OrderSide {
    ALL, // 显示本人买入和卖出的订单。
    BUY, // 仅显示自己作为买家的订单。
    SELL // 仅显示自己作为卖家的订单。
}
