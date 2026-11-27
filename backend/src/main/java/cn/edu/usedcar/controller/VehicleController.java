package cn.edu.usedcar.controller;

import cn.edu.usedcar.common.PageResult;
import cn.edu.usedcar.config.OpenApiConfig;
import cn.edu.usedcar.dto.VehicleRequest;
import cn.edu.usedcar.dto.VehicleView;
import cn.edu.usedcar.model.SessionAccount;
import cn.edu.usedcar.model.VehicleFilter;
import cn.edu.usedcar.model.VehicleStatus;
import cn.edu.usedcar.service.VehicleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 车源公开检索和发布人专属接口。账号由会话提供，写入请求不能指定发布人。
 * @author pangzhihao
 */
@RestController
@RequestMapping("/api/vehicles")
@Tag(name = "车源浏览与发布")
public class VehicleController {
    private final VehicleService vehicles;

    public VehicleController(VehicleService vehicles) {
        this.vehicles = vehicles;
    }

    @GetMapping
    @Operation(summary = "分页筛选在售车源", description = "售价以元为单位，里程以公里为单位。")
    PageResult<VehicleView> search(@Valid @ParameterObject @ModelAttribute VehicleFilter filter) {
        return vehicles.search(filter).map(VehicleView::from);
    }

    @GetMapping("/{id}")
    @Operation(summary = "查看在售车源详情")
    VehicleView detail(@PathVariable long id) {
        return VehicleView.from(vehicles.publicDetail(id));
    }

    @GetMapping("/mine")
    @Operation(summary = "查询我发布的车源", security = @SecurityRequirement(name = OpenApiConfig.SESSION_COOKIE))
    PageResult<VehicleView> mine(@AuthenticationPrincipal @Parameter(hidden = true) SessionAccount actor,
                                 @RequestParam(required = false) VehicleStatus status,
                                 @RequestParam(defaultValue = "1") int page,
                                 @RequestParam(defaultValue = "10") int pageSize) {
        return vehicles.mine(actor.profile().id(), status, page, pageSize).map(VehicleView::from);
    }

    @GetMapping("/mine/{id}")
    @Operation(summary = "查看本人车源及审核意见", security = @SecurityRequirement(name = OpenApiConfig.SESSION_COOKIE))
    VehicleView myDetail(@PathVariable long id, @AuthenticationPrincipal @Parameter(hidden = true) SessionAccount actor) {
        return VehicleView.from(vehicles.myDetail(id, actor.profile().id()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "发布车源并提交审核", security = @SecurityRequirement(name = OpenApiConfig.SESSION_COOKIE))
    VehicleView create(@Valid @RequestBody VehicleRequest request,
                       @AuthenticationPrincipal @Parameter(hidden = true) SessionAccount actor) {
        return VehicleView.from(vehicles.create(actor.profile().id(), request.toDraft()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "修正并重新提交车源", security = @SecurityRequirement(name = OpenApiConfig.SESSION_COOKIE))
    VehicleView resubmit(@PathVariable long id, @Valid @RequestBody VehicleRequest request,
                         @AuthenticationPrincipal @Parameter(hidden = true) SessionAccount actor) {
        return VehicleView.from(vehicles.resubmit(id, actor.profile().id(), request.toDraft()));
    }

    @PostMapping("/{id}/offline")
    @Operation(summary = "下架本人在售车源", security = @SecurityRequirement(name = OpenApiConfig.SESSION_COOKIE))
    VehicleView offline(@PathVariable long id, @AuthenticationPrincipal @Parameter(hidden = true) SessionAccount actor) {
        return VehicleView.from(vehicles.takeOffline(id, actor.profile().id()));
    }
}
