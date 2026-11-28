package cn.edu.usedcar.entity;

import cn.edu.usedcar.model.AttachmentKind;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 附件表的持久化模型。存储键与原始文件名分开保存。
 * @author pangzhihao
 */
@TableName("attachment")
public class AttachmentEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long vehicleId;
    private Long reportId;
    private Long uploaderId;
    private AttachmentKind kind;
    private String storageKey;
    private String name;
    private String mime;
    private Long bytes;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getVehicleId() { return vehicleId; }
    public void setVehicleId(Long vehicleId) { this.vehicleId = vehicleId; }

    public Long getReportId() { return reportId; }
    public void setReportId(Long reportId) { this.reportId = reportId; }

    public Long getUploaderId() { return uploaderId; }
    public void setUploaderId(Long uploaderId) { this.uploaderId = uploaderId; }

    public AttachmentKind getKind() { return kind; }
    public void setKind(AttachmentKind kind) { this.kind = kind; }

    public String getStorageKey() { return storageKey; }
    public void setStorageKey(String storageKey) { this.storageKey = storageKey; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getMime() { return mime; }
    public void setMime(String mime) { this.mime = mime; }

    public Long getBytes() { return bytes; }
    public void setBytes(Long bytes) { this.bytes = bytes; }
}
