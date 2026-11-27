package cn.edu.usedcar.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 统一定义 Swagger 文档中的会话和 CSRF 请求约定。
 * @author pangzhihao
 */
@Configuration
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true")
public class OpenApiConfig {
    public static final String SESSION_COOKIE = "sessionCookie";

    @Bean
    OpenAPI usedCarOpenApi() {
        return new OpenAPI()
                .info(new Info().title("二手车交易管理系统 API").version("1.0.0")
                        .contact(new Contact().name("pangzhihao"))
                        .description("同源 Swagger UI 自动携带登录会话 Cookie。写请求先调用 GET /api/auth/csrf，"
                                + "将返回的 token 填入 X-CSRF-TOKEN 请求头。登录后请重新获取令牌。"))
                .components(new Components().addSecuritySchemes(SESSION_COOKIE,
                        new SecurityScheme().type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE).name("JSESSIONID")));
    }

    @Bean
    OpenApiCustomizer sessionEndpoints() {
        return api -> {
            // 退出由 Spring Security 过滤器处理，控制器扫描不会自动发现此接口。
            api.path("/api/auth/logout", new PathItem().post(new Operation()
                    .addTagsItem("账号认证")
                    .summary("退出登录")
                    .description("销毁当前会话并清理浏览器会话 Cookie。"
                            + "已退出的用户持有效匿名会话和 CSRF 令牌再次请求时，也返回 204。")
                    .addSecurityItem(new SecurityRequirement().addList(SESSION_COOKIE))
                    .responses(new ApiResponses()
                            .addApiResponse("204", new ApiResponse().description("会话已结束"))
                            .addApiResponse("403", new ApiResponse().description("缺少有效的 CSRF 令牌")))));
            api.getPaths().forEach((path, item) -> {
                if (path.startsWith("/api/")) {
                    item.readOperationsMap().forEach((method, operation) -> {
                        switch (method) {
                            case POST, PUT, PATCH, DELETE -> operation.addParametersItem(new HeaderParameter()
                                    .name("X-CSRF-TOKEN").required(true).schema(new StringSchema())
                                    .description("由 GET /api/auth/csrf 签发。登录后需重新获取。"));
                            default -> {
                                // 读取接口无需提供 CSRF 请求头。
                            }
                        }
                    });
                }
            });
        };
    }
}
