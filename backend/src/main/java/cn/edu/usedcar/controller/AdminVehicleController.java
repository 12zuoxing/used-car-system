package cn.edu.usedcar.controller;

import cn.edu.usedcar.common.PageResult;
import cn.edu.usedcar.config.OpenApiConfig;
import cn.edu.usedcar.dto.VehicleReviewRequest;
import cn.edu.usedcar.dto.VehicleView;
import cn.edu.usedcar.model.SessionAccount;
import cn.edu.usedcar.model.VehicleStatus;
import cn.edu.usedcar.service.VehicleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工作人员和管理员的车源审核接口。
 * @author pangzhihao
 */
@RestController
@RequestMapping("/api/admin/vehicles")
@Tag(name = "后台车源审核")
@SecurityRequirement(name = OpenApiConfig.SESSION_COOKIE)
public class AdminVehicleController {
    private final VehicleService vehicles;

    public AdminVehicleController(VehicleService vehicles) {
        this.vehicles = vehicles;
    }

    @GetMapping
    @Operation(summary = "分页查看待审与已处理车源")
    PageResult<VehicleView> queue(@RequestParam(required = false) VehicleStatus status,
                                  @RequestParam(defaultValue = "1") int page,
                                  @RequestParam(defaultValue = "10") int pageSize) {
        return vehicles.reviewQueue(status, page, pageSize).map(VehicleView::from);
    }

    @PostMapping("/{id}/review")
    @Operation(summary = "通过或退回车源", description = "退回时需填写原因。上架代销车源需要先完成委托。")
    VehicleView review(@PathVariable long id, @Valid @RequestBody VehicleReviewRequest request,
                       @AuthenticationPrincipal @Parameter(hidden = true) SessionAccount actor) {
        return VehicleView.from(vehicles.review(id, actor.profile().id(), request.revision(), request.decision(),
                request.note()));
    }
}
