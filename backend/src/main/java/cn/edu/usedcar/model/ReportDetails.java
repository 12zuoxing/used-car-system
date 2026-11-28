package cn.edu.usedcar.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 车况报告查询结果。审核版本使用本轮所见的最后一个附件编号。
 * @author pangzhihao
 */
public record ReportDetails(long id, long vehicleId, String vehicleModel, String source, LocalDate reportDate,
                            String summary, ReportStatus status, String reviewNote, LocalDateTime reviewedAt,
                            Long revision, List<AttachmentInfo> attachments) {
}
