package cn.edu.usedcar.dto; // 定义后台日志筛选字段。

import java.time.LocalDate; // 让后台按日期查日志。
import jakarta.validation.constraints.Pattern; // 限定只接受合法的事件相关代码。
import jakarta.validation.constraints.Positive; // 确保业务记录编号为正数。
import org.springframework.format.annotation.DateTimeFormat; // 从表单和 URL 绑定 ISO 日期。

/** 审计分页查询，可按业务对象、操作和记录时间筛选。
 * @author pangzhihao
 */
public record AuditQuery(
        @Pattern(regexp = "[A-Z0-9_]{1,24}", message = "业务类型格式不正确。") String subjectType, // 精确匹配业务类型。
        @Pattern(regexp = "[A-Z0-9_]{1,40}", message = "操作代码格式不正确。") String action, // 精确匹配操作代码。
        @Positive(message = "业务编号须为正数。") Long subjectId, // 读取某一业务记录的操作历史。
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from, // 起始日期包含当天。
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to, // 截止日期包含当天。
        Integer page, // 未指定时从第 1 页开始。
        Integer pageSize // 未指定时使用默认页大小。
) { }
