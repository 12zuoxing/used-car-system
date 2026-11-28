package cn.edu.usedcar.model;

import java.io.InputStream;

/**
 * 请求文件的流和元数据。输入流由接口层关闭。
 * @author pangzhihao
 */
public record UploadFile(String name, String contentType, long size, InputStream stream) {
}
