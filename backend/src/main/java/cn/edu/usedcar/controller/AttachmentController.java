package cn.edu.usedcar.controller;

import java.nio.charset.StandardCharsets;
import java.util.List;

import cn.edu.usedcar.config.OpenApiConfig;
import cn.edu.usedcar.model.AttachmentInfo;
import cn.edu.usedcar.model.SessionAccount;
import cn.edu.usedcar.service.AttachmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 车辆照片、报告附件上传及鉴权下载入口。
 * @author pangzhihao
 */
@RestController
@Tag(name = "车源附件")
public class AttachmentController {
    private final AttachmentService attachments;

    public AttachmentController(AttachmentService attachments) {
        this.attachments = attachments;
    }

    @GetMapping("/api/vehicles/{vehicleId}/attachments")
    @Operation(summary = "查询车辆照片")
    List<AttachmentInfo> photos(@PathVariable long vehicleId,
                               @AuthenticationPrincipal @Parameter(hidden = true) SessionAccount actor) {
        return attachments.photos(vehicleId, actor);
    }

    @PostMapping(value = "/api/vehicles/{vehicleId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "上传照片或补充报告附件", security = @SecurityRequirement(name = OpenApiConfig.SESSION_COOKIE))
    AttachmentInfo upload(@PathVariable long vehicleId, @RequestParam(required = false) Long reportId,
                          @RequestPart MultipartFile file,
                          @AuthenticationPrincipal @Parameter(hidden = true) SessionAccount actor) {
        return MultipartUploads.withFile(file, upload -> attachments.upload(vehicleId, actor.profile().id(), reportId, upload));
    }

    @GetMapping("/api/attachments/{id}")
    @Operation(summary = "读取或下载附件", description = "每次访问均检查报告和车源的可见范围。")
    ResponseEntity<Resource> download(@PathVariable long id,
                                      @AuthenticationPrincipal @Parameter(hidden = true) SessionAccount actor) {
        var result = attachments.download(id, actor);
        // 报告附件强制下载。照片可用于相册的 img，同样使用服务器确认的媒体类型。
        var disposition = (result.inline() ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(result.info().name(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(result.info().mime()))
                .contentLength(result.info().bytes())
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(result.path()));
    }
}
