package com.falconx.gateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * gateway 基础配置入口。
 *
 * <p>Stage 4 通过该配置类完成三件事情：
 *
 * <ul>
 *   <li>启用路由和安全属性绑定</li>
 *   <li>建立北向入口到各 owner 服务的静态路由</li>
 *   <li>保持 gateway 只负责转发，不引入业务编排</li>
 * </ul>
 */
@Configuration
@EnableConfigurationProperties({GatewayRouteProperties.class, GatewaySecurityProperties.class})
public class GatewayConfiguration {

    /**
     * 提供 gateway 统一使用的 ObjectMapper。
     *
     * <p>当前 Stage 4 的 gateway 同时承担两类 JSON 处理职责：
     *
     * <ul>
     *   <li>校验 Access Token 时解析 JWT payload</li>
     *   <li>鉴权失败时输出统一 JSON 错误响应</li>
     * </ul>
     *
     * <p>在当前 `Spring Cloud Gateway + Spring Boot 4` 组合下，
     * 为避免不同自动配置路径下 `ObjectMapper` 装配不稳定，
     * 这里显式声明一个最小公共 Bean，后续若补统一 JSON 配置，
     * 也应继续从该 Bean 扩展。
     *
     * @return gateway 统一 JSON 序列化器
     */
    @Bean
    public ObjectMapper gatewayObjectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    /**
     * 建立 FalconX 一期最小路由表。
     *
     * @param builder Spring Cloud Gateway 路由构建器
     * @param properties 路由配置
     * @return 路由定位器
     */
    @Bean
    public RouteLocator falconxRouteLocator(RouteLocatorBuilder builder, GatewayRouteProperties properties) {
        return builder.routes()
                .route("identity-route", route -> route.path("/api/v1/auth/**")
                        .uri(properties.getIdentityBaseUrl().toString()))
                .route("market-route", route -> route.path("/api/v1/market/**")
                        .uri(properties.getMarketBaseUrl().toString()))
                .route("trading-route", route -> route.path("/api/v1/trading/**")
                        .uri(properties.getTradingBaseUrl().toString()))
                .route("wallet-route", route -> route.path("/api/v1/wallet/**")
                        .uri(properties.getWalletBaseUrl().toString()))
                .build();
    }
}
