package cn.edu.usedcar.dto;

import java.math.BigDecimal;

import cn.edu.usedcar.model.Fuel;
import cn.edu.usedcar.model.SaleMode;
import cn.edu.usedcar.model.Transmission;
import cn.edu.usedcar.model.VehicleDraft;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 发布或修改车辆的请求。售价单位为元，里程单位为公里。
 * @author pangzhihao
 */
public record VehicleRequest(
        @NotNull(message = "请选择品牌。") @Positive Long brandId,
        @NotNull(message = "请选择城市。") @Positive Long cityId,
        @NotBlank(message = "请输入车型。") @Size(max = 80, message = "车型最多 80 个字符。") String model,
        @NotNull(message = "请输入登记年份。") @Min(value = 1980, message = "登记年份不能早于 1980 年。")
        Integer registrationYear,
        @NotNull(message = "请输入里程。") @Min(value = 0, message = "里程不能小于零。") Integer mileage,
        @NotNull(message = "请输入售价。") @DecimalMin(value = "0.01", message = "售价必须大于零。")
        @Digits(integer = 10, fraction = 2, message = "售价最多 10 位整数、2 位小数。") BigDecimal price,
        @NotNull(message = "请选择变速箱。") Transmission transmission,
        @NotNull(message = "请选择能源类别。") Fuel fuel,
        @NotNull(message = "请选择经营方式。") SaleMode saleMode,
        @NotBlank(message = "请输入车辆描述。") @Size(max = 1000, message = "车辆描述最多 1000 个字符。")
        String description) {
    public VehicleDraft toDraft() {
        return new VehicleDraft(brandId, cityId, model, registrationYear, mileage, price,
                transmission, fuel, saleMode, description);
    }
}
