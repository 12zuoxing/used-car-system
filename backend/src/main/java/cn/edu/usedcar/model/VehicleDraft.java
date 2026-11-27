package cn.edu.usedcar.model;

import java.math.BigDecimal;

/**
 * 待提交的车辆资料。所属账号和审核状态由后端确定。
 * @author pangzhihao
 */
public record VehicleDraft(long brandId, long cityId, String model, int registrationYear, int mileage,
                           BigDecimal price, Transmission transmission, Fuel fuel, SaleMode saleMode,
                           String description) {
}
