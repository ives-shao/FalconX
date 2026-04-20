package com.falconx.market.support;

import java.time.Duration;
import java.util.function.Supplier;
import org.junit.jupiter.api.Assertions;
import org.springframework.boot.test.system.CapturedOutput;

/**
 * Tiingo 外部真源测试支撑。
 *
 * <p>这组辅助方法只服务于“显式开启的外部集成测试”：
 *
 * <ul>
 *   <li>统一轮询等待外部网络与异步日志结果</li>
 *   <li>避免每个测试类各自复制超时与输出拼接逻辑</li>
 *   <li>把失败信息收敛成可直接用于排障的断言文本</li>
 * </ul>
 */
public final class TiingoExternalTestSupport {

    private TiingoExternalTestSupport() {
    }

    /**
     * 在给定超时时间内轮询等待条件成立。
     *
     * @param timeout 最大等待时间
     * @param failureMessage 超时后的断言信息
     * @param condition 目标条件
     */
    public static void waitForCondition(Duration timeout,
                                        String failureMessage,
                                        Supplier<Boolean> condition) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.get()) {
                return;
            }
            sleepBriefly();
        }
        Assertions.fail(failureMessage);
    }

    /**
     * 等待控制台输出中出现指定关键字。
     *
     * @param output 当前测试捕获到的控制台输出
     * @param keyword 期望出现的关键字
     * @param timeout 最大等待时间
     */
    public static void waitForLog(CapturedOutput output, String keyword, Duration timeout) {
        waitForCondition(
                timeout,
                "未在超时内观察到日志关键字: " + keyword + System.lineSeparator() + combinedOutput(output),
                () -> combinedOutput(output).contains(keyword)
        );
    }

    /**
     * 拼接标准输出与错误输出，便于统一做日志包含断言。
     *
     * @param output 测试输出
     * @return 拼接后的完整文本
     */
    public static String combinedOutput(CapturedOutput output) {
        return output.getOut() + output.getErr();
    }

    private static void sleepBriefly() {
        try {
            Thread.sleep(200L);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待 Tiingo 外部测试结果时线程被中断", interruptedException);
        }
    }
}
