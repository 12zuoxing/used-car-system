package cn.edu.usedcar.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import cn.edu.usedcar.model.Fuel;
import cn.edu.usedcar.model.SaleMode;
import cn.edu.usedcar.model.Transmission;
import cn.edu.usedcar.model.VehicleRecord;
import cn.edu.usedcar.model.VehicleStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 车源 API 响应。公开查询不会包含为空的审核说明、发布人或提交版本。
 * @author pangzhihao
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record VehicleView(long id, long brandId, String brandName, long cityId, String cityName, String model,
                          int registrationYear, int mileage, BigDecimal price, Transmission transmission,
                          Fuel fuel, SaleMode saleMode, String description, VehicleStatus status,
                          LocalDateTime listedAt, String ownerName, String reviewNote,
                          LocalDateTime reviewedAt, Long revision, Long coverPhotoId) {
    public static VehicleView from(VehicleRecord vehicle) {
        return new VehicleView(vehicle.id(), vehicle.brandId(), vehicle.brandName(), vehicle.cityId(), vehicle.cityName(),
                vehicle.model(), vehicle.registrationYear(), vehicle.mileage(), vehicle.price(), vehicle.transmission(),
                vehicle.fuel(), vehicle.saleMode(), vehicle.description(), vehicle.status(), vehicle.listedAt(),
                vehicle.ownerName(), vehicle.reviewNote(), vehicle.reviewedAt(), vehicle.revision(), vehicle.coverPhotoId());
    }
}
