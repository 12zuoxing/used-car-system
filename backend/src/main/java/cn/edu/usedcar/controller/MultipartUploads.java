package cn.edu.usedcar.controller;

import java.io.IOException;
import java.util.function.Function;

import cn.edu.usedcar.common.ApiException;
import cn.edu.usedcar.model.UploadFile;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;

/**
 * 在请求作用域内打开和关闭上传流，服务层无需依赖 Servlet 上传模型。
 * @author pangzhihao
 */
final class MultipartUploads {
    private MultipartUploads() {
    }

    static <T> T withFile(MultipartFile file, Function<UploadFile, T> action) {
        if (file == null) return action.apply(null);
        try (var stream = file.getInputStream()) {
            return action.apply(new UploadFile(file.getOriginalFilename(), file.getContentType(), file.getSize(), stream));
        } catch (IOException error) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FILE", "上传文件无法读取。");
        }
    }
}
