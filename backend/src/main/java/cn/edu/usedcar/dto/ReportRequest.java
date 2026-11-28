package cn.edu.usedcar.dto;

import java.time.LocalDate;

import cn.edu.usedcar.model.ReportDraft;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 由卖家人工登记的报告信息，来源可填写查博士或其他检测机构。
 * @author pangzhihao
 */
public record ReportRequest(
        @NotBlank(message = "请输入报告来源。") @Size(max = 100) String source,
        @NotNull(message = "请选择报告日期。") LocalDate reportDate,
        @NotBlank(message = "请输入报告摘要。") @Size(max = 1200) String summary) {
    public ReportDraft toDraft() {
        return new ReportDraft(source, reportDate, summary);
    }
}
