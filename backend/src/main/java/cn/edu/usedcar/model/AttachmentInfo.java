package cn.edu.usedcar.model;

/**
 * 文件展示元数据，客户端只使用业务主键访问文件。
 * @author pangzhihao
 */
public record AttachmentInfo(long id, String name, String mime, long bytes) {
}
