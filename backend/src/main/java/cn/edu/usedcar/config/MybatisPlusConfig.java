package cn.edu.usedcar.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 注册 Mapper 代理及分页插件，后续业务模块可共用相同的数据访问配置。
 * @author pangzhihao
 */
@Configuration
@MapperScan("cn.edu.usedcar.mapper")
public class MybatisPlusConfig {
    @Bean
    MybatisPlusInterceptor mybatisPlusInterceptor() {
        // 应用支持 MySQL 和 H2 两种配置，由插件按实际数据库连接选择分页方言。
        var pagination = new PaginationInnerInterceptor();
        pagination.setMaxLimit(100L);
        var interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }
}
