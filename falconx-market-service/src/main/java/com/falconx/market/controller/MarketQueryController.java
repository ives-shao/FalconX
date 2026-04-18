package com.falconx.market.controller;

import com.falconx.common.api.ApiResponse;
import com.falconx.infrastructure.trace.TraceIdConstants;
import com.falconx.market.dto.MarketQuoteResponse;
import com.falconx.market.entity.StandardQuote;
import com.falconx.market.service.MarketQuoteQueryService;
import jakarta.validation.constraints.NotBlank;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 市场查询控制器。
 *
 * <p>该控制器负责暴露 Stage 4 最小北向查询能力：
 * 通过 gateway 转发后，外部调用方可以查询某个品种的最新报价快照。
 */
@Validated
@RestController
@RequestMapping("/api/v1/market")
public class MarketQueryController {

    private static final Logger log = LoggerFactory.getLogger(MarketQueryController.class);

    private final MarketQuoteQueryService marketQuoteQueryService;

    public MarketQueryController(MarketQuoteQueryService marketQuoteQueryService) {
        this.marketQuoteQueryService = marketQuoteQueryService;
    }

    /**
     * 查询最新报价。
     *
     * @param symbol 品种代码
     * @return 最新报价快照
     */
    @GetMapping("/quotes/{symbol}")
    public ApiResponse<MarketQuoteResponse> getLatestQuote(@PathVariable @NotBlank String symbol) {
        log.info("market.http.quote.received symbol={}", symbol);
        StandardQuote quote = marketQuoteQueryService.getLatestQuote(symbol);
        return new ApiResponse<>(
                "0",
                "success",
                new MarketQuoteResponse(
                        quote.symbol(),
                        quote.bid(),
                        quote.ask(),
                        quote.mid(),
                        quote.mark(),
                        quote.ts(),
                        quote.source(),
                        quote.stale()
                ),
                OffsetDateTime.now(),
                MDC.get(TraceIdConstants.TRACE_ID_MDC_KEY)
        );
    }
}
