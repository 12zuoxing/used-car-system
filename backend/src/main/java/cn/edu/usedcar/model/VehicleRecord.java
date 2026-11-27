package cn.edu.usedcar.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 车源查询投影。审核和发布人字段仅在管理视图中读取。
 * @author pangzhihao
 */
public record VehicleRecord(long id, long brandId, String brandName, long cityId, String cityName, String model,
                            int registrationYear, int mileage, BigDecimal price, Transmission transmission,
                            Fuel fuel, SaleMode saleMode, String description, VehicleStatus status,
                            LocalDateTime listedAt, String ownerName, String reviewNote,
                            LocalDateTime reviewedAt, Long revision) {
}
