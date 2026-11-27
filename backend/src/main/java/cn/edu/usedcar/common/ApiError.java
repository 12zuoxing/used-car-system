package cn.edu.usedcar.common;

/**
 * MVC 控制器与 Spring Security 过滤器共用的 JSON 错误结构。
 * @author pangzhihao
 */
public record ApiError(String code, String message) {
}
