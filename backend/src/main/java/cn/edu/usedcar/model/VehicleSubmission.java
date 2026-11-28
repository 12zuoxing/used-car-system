package cn.edu.usedcar.model;

/**
 * 一次发布或重提的完整载荷。照片、报告及报告附件可随车辆资料原子保存。
 * @author pangzhihao
 */
public record VehicleSubmission(VehicleDraft vehicle, UploadFile photo, ReportDraft report, UploadFile reportFile) {
    public VehicleSubmission(VehicleDraft vehicle) {
        this(vehicle, null, null, null);
    }
}
