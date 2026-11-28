package cn.edu.usedcar.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 仅返回可供界面显示的业务信息，解析异常和认证异常不向外暴露内部细节。
 * @author pangzhihao
 */
@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiError> uploadTooLarge() {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(new ApiError("FILE_TOO_LARGE", "上传文件或请求超过大小限制。"));
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> businessError(ApiException error) {
        return ResponseEntity.status(error.status()).body(new ApiError(error.code(), error.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> invalidFields(MethodArgumentNotValidException error) {
        var field = error.getBindingResult().getFieldError();
        String message = field == null ? "请检查表单内容。" : field.getDefaultMessage();
        return ResponseEntity.badRequest().body(new ApiError("INVALID_INPUT", message));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiError> malformedRequest() {
        return ResponseEntity.badRequest().body(new ApiError("INVALID_INPUT", "请求字段或格式不正确。"));
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiError> invalidCredentials() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiError("INVALID_CREDENTIALS", "账号或密码不正确，或账号已停用。"));
    }
}
