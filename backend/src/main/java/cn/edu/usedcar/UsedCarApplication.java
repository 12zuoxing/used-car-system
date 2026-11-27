package cn.edu.usedcar;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 系统启动入口，扫描账号及后续业务模块的 Spring 组件。
 * @author pangzhihao
 */
@SpringBootApplication
public class UsedCarApplication {
    public static void main(String[] args) {
        SpringApplication.run(UsedCarApplication.class, args);
    }
}
