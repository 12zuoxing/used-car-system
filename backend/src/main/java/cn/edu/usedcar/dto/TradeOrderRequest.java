package cn.edu.usedcar.dto; // 定义买家在下单时可以提供的数据。

import jakarta.validation.constraints.NotNull; // 要求请求明确指定目标车辆。
import jakarta.validation.constraints.Positive; // 约束车辆编号大于零。

/** 下单请求只接收车辆编号，价格和交易双方由服务端确认。
 * @author pangzhihao
 */
public record TradeOrderRequest(
        @NotNull(message = "请选择要购买的车辆。") @Positive Long vehicleId // 从已审核的车源读取售价和发布人。
) { }
