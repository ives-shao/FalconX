package com.falconx.trading.application;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 价格 tick 执行器配置。
 *
 * <p>市场价格事件的数据库触发链路不能直接跑在 Kafka listener 线程里，
 * 必须切到 trading-core 自管线程后再执行，以隔离高频回调线程与 owner 写路径。
 */
@Configuration
public class TradingPriceTickExecutionConfiguration {

    /**
     * 单线程执行器用于保证价格事件按接收顺序串行进入 owner 写路径，
     * 避免同一品种上的重复 tick 在本地测试环境里产生额外并发噪声。
     */
    @Bean(name = "tradingPriceTickExecutor", destroyMethod = "shutdown")
    public ExecutorService tradingPriceTickExecutor() {
        return Executors.newSingleThreadExecutor(Thread.ofPlatform()
                .name("trading-price-tick-", 0)
                .factory());
    }
}
