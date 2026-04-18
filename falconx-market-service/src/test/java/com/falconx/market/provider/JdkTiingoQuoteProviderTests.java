package com.falconx.market.provider;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * `JdkTiingoQuoteProvider` 重连原因规则测试。
 *
 * <p>这组测试不尝试启动真实 WebSocket，而是直接锁定 provider 内部的“重连原因生成规则”。
 * 这样做的原因是：
 *
 * <ul>
 *   <li>Tiingo `E` 帧与 `onClose / onError` 的关联是纯字符串与分类逻辑，不值得为了它引入网络级测试复杂度</li>
 *   <li>一旦日志关键字或重连原因格式发生回归，这组测试可以第一时间提示“排障语义被改坏了”</li>
 * </ul>
 */
class JdkTiingoQuoteProviderTests {

    @Test
    void shouldRunCallbackWithApplicationClassLoaderAndRestorePreviousContext() {
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        ClassLoader dummy = new ClassLoader(null) {
        };
        final ClassLoader[] observed = new ClassLoader[1];

        Thread.currentThread().setContextClassLoader(dummy);
        try {
            JdkTiingoQuoteProvider.runWithApplicationClassLoader(
                    () -> observed[0] = Thread.currentThread().getContextClassLoader()
            );

            Assertions.assertSame(JdkTiingoQuoteProvider.class.getClassLoader(), observed[0]);
            Assertions.assertSame(dummy, Thread.currentThread().getContextClassLoader());
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
    }

    @Test
    void shouldBuildServerErrorHintFromErrorFrame() {
        String reconnectHint = JdkTiingoQuoteProvider.buildServerErrorHint(
                new TiingoInboundFrameMetadata("E", "fx", null, 401, "Unauthorized")
        );

        Assertions.assertEquals("server-error[service=fx,code=401,message=Unauthorized]", reconnectHint);
    }

    @Test
    void shouldClassifyCommonCloseCodes() {
        Assertions.assertEquals("normal-closure", JdkTiingoQuoteProvider.classifyCloseStatus(1000));
        Assertions.assertEquals("policy-violation", JdkTiingoQuoteProvider.classifyCloseStatus(1008));
        Assertions.assertEquals("status-3999", JdkTiingoQuoteProvider.classifyCloseStatus(3999));
    }

    @Test
    void shouldLinkCloseReasonWithLatestServerError() {
        TiingoInboundFrameMetadata serverError = new TiingoInboundFrameMetadata("E", "fx", null, 429, "Rate limit");

        String reconnectReason = JdkTiingoQuoteProvider.buildReconnectReasonForClose(
                1008,
                "policy violation",
                serverError
        );

        Assertions.assertEquals(
                "close[policy-violation,status=1008,reason=policy violation] linked-server-error[service=fx,code=429,message=Rate limit]",
                reconnectReason
        );
    }

    @Test
    void shouldBuildCloseReasonWithoutServerErrorContext() {
        String reconnectReason = JdkTiingoQuoteProvider.buildReconnectReasonForClose(1000, "", null);

        Assertions.assertEquals("close[normal-closure,status=1000,reason=none]", reconnectReason);
    }

    @Test
    void shouldLinkThrowableWithLatestServerError() {
        TiingoInboundFrameMetadata serverError = new TiingoInboundFrameMetadata("E", "fx", null, 401, "Unauthorized");

        String reconnectReason = JdkTiingoQuoteProvider.buildReconnectReasonForError(
                new IllegalStateException("connection reset"),
                serverError
        );

        Assertions.assertEquals(
                "error[type=IllegalStateException,message=connection reset] linked-server-error[service=fx,code=401,message=Unauthorized]",
                reconnectReason
        );
    }
}
