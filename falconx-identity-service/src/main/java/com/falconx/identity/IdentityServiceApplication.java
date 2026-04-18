package com.falconx.identity;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * FalconX identity-service 启动类。
 *
 * <p>当前阶段仅建立身份服务的可启动骨架，
 * 后续将在该服务中逐步补充注册、登录、JWT、用户状态管理以及入金激活事件消费能力。
 */
@SpringBootApplication(scanBasePackages = {
        "com.falconx.identity",
        "com.falconx.infrastructure"
})
@MapperScan("com.falconx.identity.repository.mapper")
public class IdentityServiceApplication {

    /**
     * identity-service 进程入口。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(IdentityServiceApplication.class, args);
    }
}
