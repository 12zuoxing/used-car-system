package cn.edu.usedcar.controller;

import cn.edu.usedcar.common.PageResult;
import cn.edu.usedcar.config.OpenApiConfig;
import cn.edu.usedcar.controller.VehicleReportController.ReportView;
import cn.edu.usedcar.dto.VehicleReviewRequest;
import cn.edu.usedcar.model.ReportStatus;
import cn.edu.usedcar.model.SessionAccount;
import cn.edu.usedcar.service.ReportService;
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
 * 管理端的报告审核列表与处理接口。
 * @author pangzhihao
 */
@RestController
@RequestMapping("/api/admin/reports")
@Tag(name = "后台车况报告审核")
@SecurityRequirement(name = OpenApiConfig.SESSION_COOKIE)
public class AdminReportController {
    private final ReportService reports;

    public AdminReportController(ReportService reports) {
        this.reports = reports;
    }

    @GetMapping
    @Operation(summary = "分页查询车况报告")
    PageResult<ReportView> queue(@RequestParam(required = false) ReportStatus status,
                                @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int pageSize) {
        return reports.queue(status, page, pageSize).map(ReportView::from);
    }

    @PostMapping("/{id}/review")
    @Operation(summary = "审核通过或退回车况报告")
    ReportView review(@PathVariable long id, @Valid @RequestBody VehicleReviewRequest request,
                      @AuthenticationPrincipal @Parameter(hidden = true) SessionAccount actor) {
        return ReportView.from(reports.review(id, actor.profile().id(), request.revision(), request.decision(), request.note()));
    }
}
