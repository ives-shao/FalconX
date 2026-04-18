package com.falconx.trading.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * trading-core-service 配置入口。
 *
 * <p>当前阶段该配置类只负责开启交易核心属性绑定。
 * 后续当 Stage 3B 逐步接入真实数据库、Kafka 和 Redis 实现时，
 * 统一在该包下继续扩展，但不应改变“应用层编排 + 服务层规则 + 仓储层 owner 数据”的分层方向。
 */
@Configuration
@EnableKafka
@EnableConfigurationProperties(TradingCoreServiceProperties.class)
public class TradingCoreServiceConfiguration {

    /**
     * 交易核心统一 JSON 序列化器。
     *
     * <p>Stage 5 开始，交易核心需要把事件 payload 真正落入 `t_outbox`，而这些 payload 中包含
     * `OffsetDateTime` 等 Java 时间类型。这里显式注册统一 `ObjectMapper`，避免各仓储层各自创建
     * 没有时间模块的 JSON 序列化器，导致真实持久化链路在运行时失败。
     *
     * @return 已自动注册常用模块的 JSON 序列化器
     */
    @Bean
    ObjectMapper objectMapper() {
        return JsonMapper.builder()
                .findAndAddModules()
                .build();
    }

    /**
     * 注册 Kafka 默认监听容器工厂。
     *
     * <p>交易核心存在多个 `@KafkaListener` 入口，这里统一复用 Spring Kafka 自动配置的
     * `ConsumerFactory` 构造默认容器工厂，避免监听器因缺少默认 Bean 名称而无法启动。
     *
     * @param consumerFactory Spring Kafka 自动配置好的消费者工厂
     * @return 默认 Kafka 监听容器工厂
     */
    @Bean(name = "kafkaListenerContainerFactory")
    ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory
    ) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        return factory;
    }
}
