package cn.edu.usedcar.model;

import java.time.LocalDate;

/**
 * 提交审核的车况报告内容，由接口层校验字段。
 * @author pangzhihao
 */
public record ReportDraft(String source, LocalDate reportDate, String summary) {
}
