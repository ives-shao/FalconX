package com.falconx.market.application;

import com.falconx.infrastructure.id.IdGenerator;
import com.falconx.market.config.MarketServiceProperties;
import com.falconx.market.provider.TiingoCryptoSymbolSamplingService;
import com.falconx.market.provider.TiingoDiscoveredCryptoSymbol;
import com.falconx.market.repository.MarketSymbolRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * `TiingoCryptoSymbolImportRunner` 单元测试。
 *
 * <p>这里重点锁定两条启动链路规则：
 *
 * <ul>
 *   <li>symbol 采样必须异步后台执行，不能阻塞 Spring 启动线程</li>
 *   <li>采样失败只记录告警，不应向上抛异常导致服务启动失败</li>
 * </ul>
 */
class TiingoCryptoSymbolImportRunnerTests {

    @Test
    void shouldImportSymbolsInBackgroundWithoutBlockingCallerThread() throws Exception {
        MarketServiceProperties properties = new MarketServiceProperties();
        properties.getTiingo().getCryptoSymbolImport().setEnabled(true);

        TiingoCryptoSymbolSamplingService samplingService = mock(TiingoCryptoSymbolSamplingService.class);
        when(samplingService.sample()).thenReturn(List.of(
                new TiingoDiscoveredCryptoSymbol(
                        "SOLUSDT",
                        "SOL",
                        "USDT",
                        "binance",
                        OffsetDateTime.now(),
                        new BigDecimal("126.968"),
                        new BigDecimal("8.39")
                )
        ));

        MarketSymbolRepository marketSymbolRepository = mock(MarketSymbolRepository.class);
        CountDownLatch importCompleted = new CountDownLatch(1);
        doAnswer(invocation -> {
            importCompleted.countDown();
            return 1;
        }).when(marketSymbolRepository).appendIfAbsent(anyList());

        IdGenerator idGenerator = mock(IdGenerator.class);
        when(idGenerator.nextId()).thenReturn(1L);

        TiingoCryptoSymbolImportRunner runner = new TiingoCryptoSymbolImportRunner(
                properties,
                samplingService,
                marketSymbolRepository,
                idGenerator
        );

        long startedAt = System.nanoTime();
        runner.run(null);
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

        Assertions.assertTrue(elapsedMillis < 500, "run() should return quickly, elapsedMillis=" + elapsedMillis);
        Assertions.assertTrue(importCompleted.await(2, TimeUnit.SECONDS));
        verify(marketSymbolRepository).appendIfAbsent(anyList());
    }

    @Test
    void shouldSwallowSamplingFailureAndKeepStartupPathAlive() throws Exception {
        MarketServiceProperties properties = new MarketServiceProperties();
        properties.getTiingo().getCryptoSymbolImport().setEnabled(true);

        TiingoCryptoSymbolSamplingService samplingService = mock(TiingoCryptoSymbolSamplingService.class);
        CountDownLatch attempted = new CountDownLatch(1);
        doAnswer(invocation -> {
            attempted.countDown();
            throw new IllegalStateException("sampling failed");
        }).when(samplingService).sample();

        MarketSymbolRepository marketSymbolRepository = mock(MarketSymbolRepository.class);
        IdGenerator idGenerator = mock(IdGenerator.class);

        TiingoCryptoSymbolImportRunner runner = new TiingoCryptoSymbolImportRunner(
                properties,
                samplingService,
                marketSymbolRepository,
                idGenerator
        );

        Assertions.assertDoesNotThrow(() -> runner.run(null));
        Assertions.assertTrue(attempted.await(2, TimeUnit.SECONDS));
        verify(marketSymbolRepository, never()).appendIfAbsent(anyList());
    }
}
