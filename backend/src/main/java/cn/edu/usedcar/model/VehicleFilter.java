package cn.edu.usedcar.model;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

/**
 * 车源公开查询条件。空值表示不限制对应字段。
 * @author pangzhihao
 */
public record VehicleFilter(
        @Positive Long brandId, @Positive Long cityId, SaleMode saleMode,
        @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal minPrice,
        @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal maxPrice,
        @Min(1980) Integer minYear, @Min(1980) Integer maxYear,
        @Min(0) Integer minMileage, @Min(0) Integer maxMileage,
        @Min(1) Integer page, @Min(1) @Max(100) Integer pageSize) {
}
