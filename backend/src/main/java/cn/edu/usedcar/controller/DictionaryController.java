package cn.edu.usedcar.controller;

import cn.edu.usedcar.config.OpenApiConfig;
import cn.edu.usedcar.dto.DictionaryRequest;
import cn.edu.usedcar.model.DictionaryCatalog;
import cn.edu.usedcar.model.DictionaryKind;
import cn.edu.usedcar.model.SessionAccount;
import cn.edu.usedcar.service.DictionaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公共字典读取和管理员维护入口。公开接口只返回启用项。
 * @author pangzhihao
 */
@RestController
@Tag(name = "品牌与城市字典")
public class DictionaryController {
    private final DictionaryService dictionaries;

    public DictionaryController(DictionaryService dictionaries) {
        this.dictionaries = dictionaries;
    }

    @GetMapping("/api/dictionaries")
    @Operation(summary = "查询可用品牌和城市")
    DictionaryCatalog active() {
        return dictionaries.catalog(false);
    }

    @GetMapping("/api/admin/dictionaries")
    @Operation(summary = "查询全部字典项", security = @SecurityRequirement(name = OpenApiConfig.SESSION_COOKIE))
    DictionaryCatalog all() {
        return dictionaries.catalog(true);
    }

    @PostMapping("/api/admin/dictionaries/{kind}")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "新增字典项", security = @SecurityRequirement(name = OpenApiConfig.SESSION_COOKIE))
    DictionaryCatalog.Item create(@PathVariable DictionaryKind kind, @Valid @RequestBody DictionaryRequest request,
                                  @AuthenticationPrincipal @Parameter(hidden = true) SessionAccount actor) {
        return dictionaries.create(kind, request.name(), request.active(), actor.profile().id());
    }

    @PutMapping("/api/admin/dictionaries/{kind}/{id}")
    @Operation(summary = "修改名称或启停字典项", security = @SecurityRequirement(name = OpenApiConfig.SESSION_COOKIE))
    DictionaryCatalog.Item update(@PathVariable DictionaryKind kind, @PathVariable long id,
                                  @Valid @RequestBody DictionaryRequest request,
                                  @AuthenticationPrincipal @Parameter(hidden = true) SessionAccount actor) {
        return dictionaries.update(kind, id, request.name(), request.active(), actor.profile().id());
    }
}
