package cn.edu.usedcar.common;

import org.springframework.http.HttpStatus;

/**
 * 业务失败同时携带 HTTP 状态和稳定的错误码，页面可按错误码处理。
 * @author pangzhihao
 */
public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
