INSERT INTO t_symbol (
    id, symbol, category, base_currency, quote_currency, price_precision, qty_precision,
    market_code, min_qty, max_qty, min_notional, max_leverage, taker_fee_rate, spread, status
) VALUES
(1, 'BTCUSDT', 1, 'BTC', 'USDT', 2, 6, 'CRYPTO', 0.000001, 100.000000, 10.000000, 100, 0.000500, 0.50000000, 1),
(2, 'ETHUSDT', 1, 'ETH', 'USDT', 2, 5, 'CRYPTO', 0.000010, 1000.000000, 10.000000, 100, 0.000500, 0.10000000, 1),
(3, 'EURUSD', 2, 'EUR', 'USD', 5, 2, 'FX', 100.000000, 10000000.000000, 100.000000, 500, 0.000100, 0.00010000, 1),
(4, 'XAUUSD', 3, 'XAU', 'USD', 2, 2, 'METAL', 0.010000, 1000.000000, 50.000000, 200, 0.000200, 0.30000000, 1)
ON DUPLICATE KEY UPDATE
    category = VALUES(category),
    market_code = VALUES(market_code),
    base_currency = VALUES(base_currency),
    quote_currency = VALUES(quote_currency),
    price_precision = VALUES(price_precision),
    qty_precision = VALUES(qty_precision),
    min_qty = VALUES(min_qty),
    max_qty = VALUES(max_qty),
    min_notional = VALUES(min_notional),
    max_leverage = VALUES(max_leverage),
    taker_fee_rate = VALUES(taker_fee_rate),
    spread = VALUES(spread),
    status = VALUES(status);

INSERT INTO t_trading_hours (
    id, symbol, day_of_week, session_no, open_time, close_time, timezone, enabled, effective_from, effective_to
) VALUES
(101, 'BTCUSDT', 1, 1, '00:00:00', '23:59:59', 'UTC', 1, '2026-01-01', NULL),
(102, 'BTCUSDT', 2, 1, '00:00:00', '23:59:59', 'UTC', 1, '2026-01-01', NULL),
(103, 'BTCUSDT', 3, 1, '00:00:00', '23:59:59', 'UTC', 1, '2026-01-01', NULL),
(104, 'BTCUSDT', 4, 1, '00:00:00', '23:59:59', 'UTC', 1, '2026-01-01', NULL),
(105, 'BTCUSDT', 5, 1, '00:00:00', '23:59:59', 'UTC', 1, '2026-01-01', NULL),
(106, 'BTCUSDT', 6, 1, '00:00:00', '23:59:59', 'UTC', 1, '2026-01-01', NULL),
(107, 'BTCUSDT', 7, 1, '00:00:00', '23:59:59', 'UTC', 1, '2026-01-01', NULL),
(111, 'ETHUSDT', 1, 1, '00:00:00', '23:59:59', 'UTC', 1, '2026-01-01', NULL),
(112, 'ETHUSDT', 2, 1, '00:00:00', '23:59:59', 'UTC', 1, '2026-01-01', NULL),
(113, 'ETHUSDT', 3, 1, '00:00:00', '23:59:59', 'UTC', 1, '2026-01-01', NULL),
(114, 'ETHUSDT', 4, 1, '00:00:00', '23:59:59', 'UTC', 1, '2026-01-01', NULL),
(115, 'ETHUSDT', 5, 1, '00:00:00', '23:59:59', 'UTC', 1, '2026-01-01', NULL),
(116, 'ETHUSDT', 6, 1, '00:00:00', '23:59:59', 'UTC', 1, '2026-01-01', NULL),
(117, 'ETHUSDT', 7, 1, '00:00:00', '23:59:59', 'UTC', 1, '2026-01-01', NULL),
(121, 'EURUSD', 1, 1, '00:00:00', '23:59:59', 'UTC', 1, '2026-01-01', NULL),
(122, 'EURUSD', 2, 1, '00:00:00', '23:59:59', 'UTC', 1, '2026-01-01', NULL),
(123, 'EURUSD', 3, 1, '00:00:00', '23:59:59', 'UTC', 1, '2026-01-01', NULL),
(124, 'EURUSD', 4, 1, '00:00:00', '23:59:59', 'UTC', 1, '2026-01-01', NULL),
(125, 'EURUSD', 5, 1, '00:00:00', '23:59:59', 'UTC', 1, '2026-01-01', NULL),
(131, 'XAUUSD', 1, 1, '00:00:00', '23:59:59', 'UTC', 1, '2026-01-01', NULL),
(132, 'XAUUSD', 2, 1, '00:00:00', '23:59:59', 'UTC', 1, '2026-01-01', NULL),
(133, 'XAUUSD', 3, 1, '00:00:00', '23:59:59', 'UTC', 1, '2026-01-01', NULL),
(134, 'XAUUSD', 4, 1, '00:00:00', '23:59:59', 'UTC', 1, '2026-01-01', NULL),
(135, 'XAUUSD', 5, 1, '00:00:00', '23:59:59', 'UTC', 1, '2026-01-01', NULL)
ON DUPLICATE KEY UPDATE
    open_time = VALUES(open_time),
    close_time = VALUES(close_time),
    timezone = VALUES(timezone),
    enabled = VALUES(enabled),
    effective_to = VALUES(effective_to);
