package com.falconx.market.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * market-service 基础配置入口。
 *
 * <p>当前配置类负责启用配置属性绑定和 market-service 自用的 JSON 序列化能力。
 * 外部 Tiingo WebSocket 客户端本身由 Provider 层管理，不在这里直接创建额外客户端 Bean，
 * 以避免把外部源接入逻辑与基础配置混在一起。
 */
@Configuration
@EnableConfigurationProperties(MarketServiceProperties.class)
public class MarketServiceConfiguration {

    /**
     * 注册 market-service 自用的 JSON 序列化器。
     *
     * <p>当前服务内部的 Kafka publisher 以及若干测试代码都基于 `com.fasterxml.jackson`
     * 进行序列化。这里显式提供统一的 `ObjectMapper`，避免依赖 Spring Boot 4 另一套
     * 默认 JSON 自动配置而导致 Bean 缺失或 Java Time 模块不一致。
     *
     * @return 统一的 Jackson ObjectMapper
     */
    @Bean
    ObjectMapper objectMapper() {
        return JsonMapper.builder()
                .findAndAddModules()
                .build();
    }
}
