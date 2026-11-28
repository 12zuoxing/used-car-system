package cn.edu.usedcar.controller;

import java.util.List;

import cn.edu.usedcar.config.OpenApiConfig;
import cn.edu.usedcar.dto.ReportRequest;
import cn.edu.usedcar.model.ReportDetails;
import cn.edu.usedcar.model.SessionAccount;
import cn.edu.usedcar.service.ReportService;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 卖家登记和浏览车况报告的 API。
 * @author pangzhihao
 */
@RestController
@RequestMapping("/api/vehicles/{vehicleId}/reports")
@Tag(name = "车况报告")
public class VehicleReportController {
    private final ReportService reports;

    public VehicleReportController(ReportService reports) {
        this.reports = reports;
    }

    @GetMapping
    @Operation(summary = "查询车辆报告", description = "公开浏览仅返回通过审核的报告。")
    List<ReportView> list(@PathVariable long vehicleId,
                          @AuthenticationPrincipal @Parameter(hidden = true) SessionAccount actor) {
        return reports.list(vehicleId, actor).stream().map(ReportView::from).toList();
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "登记报告", security = @SecurityRequirement(name = OpenApiConfig.SESSION_COOKIE))
    ReportView create(@PathVariable long vehicleId, @Valid @RequestBody ReportRequest report,
                      @AuthenticationPrincipal @Parameter(hidden = true) SessionAccount actor) {
        return ReportView.from(reports.create(vehicleId, actor.profile().id(), report.toDraft(), null));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "登记报告并上传附件", security = @SecurityRequirement(name = OpenApiConfig.SESSION_COOKIE))
    ReportView createWithAttachment(@PathVariable long vehicleId, @RequestPart("report") @Valid ReportRequest report,
                                    @RequestPart(value = "file", required = false) MultipartFile file,
                                    @AuthenticationPrincipal @Parameter(hidden = true) SessionAccount actor) {
        return MultipartUploads.withFile(file,
                upload -> ReportView.from(reports.create(vehicleId, actor.profile().id(), report.toDraft(), upload)));
    }

    /** 过滤掉仅限本人和工作人员查看的空字段。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ReportView(long id, long vehicleId, String vehicleModel, String source, java.time.LocalDate reportDate,
                             String summary, cn.edu.usedcar.model.ReportStatus status, String reviewNote,
                             java.time.LocalDateTime reviewedAt, Long revision,
                             List<cn.edu.usedcar.model.AttachmentInfo> attachments) {
        static ReportView from(ReportDetails report) {
            return new ReportView(report.id(), report.vehicleId(), report.vehicleModel(), report.source(), report.reportDate(),
                    report.summary(), report.status(), report.reviewNote(), report.reviewedAt(), report.revision(), report.attachments());
        }
    }
}
