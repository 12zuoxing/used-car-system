package cn.edu.usedcar.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import cn.edu.usedcar.model.ReportStatus;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 车况报告的数据模型。退回与通过记录保留原报告内容。
 * @author pangzhihao
 */
@TableName("vehicle_report")
public class VehicleReportEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long vehicleId;
    private Long authorId;
    private String source;
    private LocalDate reportDate;
    private String summary;
    private ReportStatus status;
    private String reviewNote;
    private Long reviewerId;
    private LocalDateTime reviewedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getVehicleId() { return vehicleId; }
    public void setVehicleId(Long vehicleId) { this.vehicleId = vehicleId; }

    public Long getAuthorId() { return authorId; }
    public void setAuthorId(Long authorId) { this.authorId = authorId; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public LocalDate getReportDate() { return reportDate; }
    public void setReportDate(LocalDate reportDate) { this.reportDate = reportDate; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public ReportStatus getStatus() { return status; }
    public void setStatus(ReportStatus status) { this.status = status; }

    public String getReviewNote() { return reviewNote; }
    public void setReviewNote(String reviewNote) { this.reviewNote = reviewNote; }

    public Long getReviewerId() { return reviewerId; }
    public void setReviewerId(Long reviewerId) { this.reviewerId = reviewerId; }

    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
}
