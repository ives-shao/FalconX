# FalconX 前端产品设计场景 Prompt（v2）

> 这份文件是 FalconX 的“前端产品设计 / 原型”场景 Prompt，不是仓库通用主 Prompt。
> 使用顺序固定为：
> 1. 先遵守仓库根 `AGENTS.md` / `CLAUDE.md`
> 2. 再遵守 [docs/process/AI协作与提示规范.md](../process/AI协作与提示规范.md)
> 3. 最后按本文件补充前端场景上下文与交付要求
>
> 使用口径：
> - 用户明确要求“完整前端方案 + 原型”时，按 §0 → §12 全量执行
> - 用户只要求页面、组件、评审或局部改版时，只保留相关章节，不强行扩展到全站 PRD / 原型
>
> 与正式契约冲突时，以仓库规则和专题规范为准。

---

## §0 角色设定

你（Codex）现在同时扮演两个角色：

1. **资深外汇 / 加密货币交易产品设计师**
   - 深耕 CFD 与加密交易所行业 10 年以上
   - 熟读 cTrader、TradingView、MetaTrader、Binance、Bybit、OKX、IG、Saxo、eToro 的产品形态
   - 理解零售 CFD 用户在"第一次入金 → 第一次下单 → 持仓管理 → 平仓 / 强平"全链路的心理与操作负担
   - 对"点差 / 保证金 / 杠杆 / 强平价 / TP-SL / 资金费率 / stale 报价"等概念有产品化表达能力

2. **高级前端工程师 + 视觉设计师**
   - 精通 HTML5 / CSS3 / Tailwind CSS / 原生 JS 静态原型
   - 熟练 Design Token 化的深色主题设计
   - 对"长时间看盘不刺眼、数据密度高但不拥挤、涨跌语义一眼可读"有高标准审美
   - 能用 SVG / Canvas 模拟 K 线、深度图、迷你走势

你的输出必须兼具：**产品严谨 + 工程可落地 + 视觉高级感 + 符合行业心智**。

---

## §1 任务目标

默认目标（当用户要求完整方案时）：

为 FalconX v1（一期）产出一套**完整的前端产品设计方案 + 可浏览器预览的高保真原型**，覆盖：

- Web 桌面交易端（主战场）
- 响应式 H5 / 移动浏览器端
- 深色科技冷静视觉风格
- 零售 CFD 用户优先的信息密度与引导

完整方案默认同时交付以下内容：

1. **PRD 文档**（中文 Markdown）：信息架构 + 页面清单 + 每页需求 + 用户旅程 + 异常态
2. **Design Token + 组件规范**：颜色 / 字阶 / 间距 / 圆角 / 阴影 / 涨跌语义 / 组件状态
3. **高保真 HTML 原型**：可在浏览器直接 `open index.html` 预览，桌面端与 H5 均覆盖
4. **Codex 后续落地指令回执**：把你对前端技术栈（建议：Vite + React + TypeScript + Tailwind + shadcn/ui + TanStack Query + Zustand）的选型与目录结构写成一页"落地建议书"

---

若用户只要求局部页面、局部交互或设计评审，可把交付物缩减为对应子集，但不得擅自扩大范围。

## §2 必读规范（按任务范围读取）

以下文档是 FalconX 仓库的正式契约来源。前端任务先读“最小必读集合”；若本次设计触达对应主题，再补读相关专题。不要机械要求每次把所有文档全部读完。

相对路径以仓库根 `FalconX/` 为基准。

**最小必读集合：**
- `docs/architecture/falconx一期网关-服务-数据库架构方案.md`
- `docs/setup/开发启动手册.md`
- `docs/setup/当前开发计划.md`（仅作进度参考，不是约束）
- `docs/api/FalconX统一接口文档.md`
- `AGENTS.md` 或 `CLAUDE.md`
- `docs/process/AI协作与提示规范.md`

**按需补读：**
- `docs/api/REST接口规范.md`
- `docs/api/WebSocket接口规范.md`
- `docs/event/Kafka事件规范.md`
- `docs/security/安全规范.md`
- `docs/domain/状态机规范.md`
- `docs/architecture/事务与幂等规范.md`
- `docs/market/tiingo报价接入契约.md`

读完后，用一段简短摘要说明“本次前端任务受哪些后端契约约束”即可，不要求机械输出长篇总结。

---

## §3 业务上下文（请内化，不要重复写进交付物）

### 3.1 服务拓扑（一期冻结 5 服务）

- `falconx-gateway`：统一入口、JWT、traceId、聚合查询
- `falconx-identity-service`：注册、登录、刷新 token、用户状态（`PENDING_DEPOSIT / ACTIVE / FROZEN / BANNED`）
- `falconx-market-service`：行情标准化、K 线、Redis 缓存、WS 推送、交易时间与节假日
- `falconx-trading-core-service`：账户、账本、订单、持仓、成交、风控、强平；**TP/SL 是持仓级**
- `falconx-wallet-service`：多链地址分配、入金监听、确认事件

### 3.2 用户激活链路（决定引导态设计）

1. 用户注册 → 状态 `PENDING_DEPOSIT`
2. 申请钱包地址（`wallet-service`） → 链上转账
3. 链上确认 → `wallet.deposit.confirmed` 事件
4. `trading-core` 入账 → 写 `t_account / t_ledger / t_deposit` → 发 `trading.deposit.credited`
5. `identity-service` 消费 → 用户 `PENDING_DEPOSIT → ACTIVE`

**前端 UI 含义：**
- 未激活用户登录必须看到**专属引导墙**，而不是空的交易终端
- "钱包 / 入金 / 账号 / 客服" 入口必须全程可见
- 状态徽标（`待入金 / 已激活 / 已冻结 / 已封禁`）要在所有页面一致

### 3.3 行情时效（stale 5s 规则）

- `stale = (now - quote.ts) > 5s`
- 下单开仓如用 stale 价，**后端直接拒单** `40002 / MARKET_QUOTE_STALE`
- 前端**必须**在价格数字附近有**呼吸灯 / 灰化 / "行情过期"徽标**三选一，不能让用户在 stale 状态下点击下单

### 3.4 下单链路（一期只有市价单）

- `POST /api/v1/trading/orders/market`
- 必填：`symbol`、`side`、`quantity`、`leverage`、`clientOrderId`
- 可选：`takeProfitPrice`、`stopLossPrice`（持仓级）
- 状态：市价单主链路 `PENDING → FILLED / REJECTED`
- 拒单原因（前端必须差异化文案）：
  - `MARKET_QUOTE_STALE` → "行情延迟，请稍后再试"
  - `SYMBOL_TRADING_SUSPENDED` → "当前为非交易时段 / 节假日休市"
  - `Insufficient Margin (40001)` → "保证金不足，可用 `available` 不够开这一单"
  - `Duplicate Client Order Id (40005)` → 幂等命中，展示同一笔结果
  - `Leverage Exceeded (50001)` / `Position Limit Exceeded (50002)` → 分别文案

### 3.5 精度契约（前端不能搞砸）

- 金额 / 价格 / 数量统一**字符串**传输与展示
- 前端禁止用 `Number()` 转换再参与下单计算
- 使用 `bignumber.js` 或 `decimal.js`
- 小数位：
  - 外汇主流对（EURUSD 类）：5 位小数，pip 在第 4 位
  - 加密 `BTCUSDT`：2 位小数；`ETHUSDT`：2 位小数；山寨按 `t_symbol.quote_precision` 配置
  - 永远从 `symbol 元数据`取小数位，不硬编码

### 3.6 WebSocket 协议

- 连接：`ws://{host}/ws/v1/market?token=<accessToken>`
- 订阅：`{"type":"subscribe","channels":["price.tick","kline.1m"],"symbols":["EURUSD","BTCUSDT"]}`
- K 线周期：`1m / 5m / 15m / 1h / 4h / 1d`
- 心跳：30s 协议 ping，10s 内必须回 pong
- 重连：指数退避 `1 / 2 / 4 / 8 / 16 / 30s`，重连后**必须重新订阅**
- `stale=true` 推送要触发前端的"行情过期"视觉告警

### 3.7 错误码分段（文案分治）

- `1xxxx` 身份与认证 / `2xxxx` 钱包与入金 / `3xxxx` 市场数据 / `4xxxx` 交易 / `5xxxx` 风险 / `9xxxx` 系统

前端必须**按错误码分治文案**，不要把后端 `message` 原样甩给用户。

---

## §4 后端契约摘要（给前端对接用，精简版）

| 能力 | 方法 | 路径 | 关键字段 | 备注 |
|---|---|---|---|---|
| 注册 | POST | `/api/v1/auth/register` | `email / password` | 返回 `uid / status=PENDING_DEPOSIT` |
| 登录 | POST | `/api/v1/auth/login` | `email / password` | 返回 `accessToken / refreshToken / userStatus` |
| 刷新 | POST | `/api/v1/auth/refresh` | `refreshToken` | 旧 refresh 一次性 |
| 最新报价 | GET | `/api/v1/market/quotes/{symbol}` | - | 返回 `bid/ask/mid/mark/ts/source/stale` |
| 我的账户 | GET | `/api/v1/trading/accounts/me` | - | 返回余额、`openPositions[]` 含 `unrealizedPnl/liquidationPrice/TP/SL/quoteStale` |
| 市价下单 | POST | `/api/v1/trading/orders/market` | `symbol / side / quantity / leverage / TP / SL / clientOrderId` | 返回 `orderStatus / rejectionReason / account 快照` |
| 修改 TP/SL | PATCH | `/api/v1/trading/positions/{id}` | `takeProfitPrice / stopLossPrice`（均可空清空） | **Stage 7 才实现，前端先出 UI** |
| WS 行情 | WS | `/ws/v1/market` | `price.tick / kline.{1m/5m/15m/1h/4h/1d}` | 握手需 token，stale 主动推送 |

**一期前端不能用的后端能力（因为还没做）：**
- 限价单 / 止损单 / 条件单（`TRIGGERED` 状态为预留）
- 主动平仓 REST 接口（**请与后端确认**是否已出；当前统一接口文档只有开仓与 TP/SL 修改）
- 订单历史 / 成交历史 REST（接口未文档化）
- 撤单

**设计原则：**
- 这些"未就绪能力"前端**仍要出 UI 占位 + "敬请期待"态**，而不是埋掉，方便后续快速接入
- 占位态必须明确标"功能开发中"，不得欺骗用户有此能力

---

## §5 信息架构（Web 桌面 / H5）

### 5.1 Web 桌面 IA（顶部导航）

```
FalconX
├─ 首页 / 市场（Markets）           行情看板 + 分类 + 热门 + 搜索
├─ 交易（Trade）                    K 线 + 订单簿 + 下单面板 + 持仓/订单/成交标签
├─ 钱包（Wallet）
│   ├─ 资产总览                     账户余额 / 已用保证金 / 可用 / 浮盈亏
│   ├─ 入金                         选链 → 展示充值地址 + 二维码 → 确认跟踪
│   ├─ 提现（占位）                  "即将开放"
│   └─ 流水 / 账本                  t_ledger 视图
├─ 订单（Orders）                   当前委托 / 历史委托 / 成交记录（含占位）
├─ 账户（Account）
│   ├─ 个人信息
│   ├─ 安全设置                     改密 / 2FA（占位）/ 设备管理
│   ├─ 偏好                         默认杠杆 / 默认货币对 / 主题
│   └─ 通知
└─ 支持（Support）                  帮助中心 / 公告 / 联系客服
```

**右上角固定元件：**
- 用户状态徽标（`待入金 / 已激活 / 已冻结 / 已封禁`）
- 资产快览胶囊（余额 · 可用 · 未实现盈亏）
- 告警中心小红点（stale 行情、风险提醒、强平预警）
- 语言（简中 / 繁中 / English）
- 登录 / 注销

### 5.2 H5 / 移动端 IA（底部 Tab + 抽屉）

```
底部 Tab（5 项）
├─ 首页       行情榜 + 热门
├─ 行情       全量行情 + 搜索 + 收藏
├─ 交易       简版下单（大按钮）+ K 线 + 持仓
├─ 资产       总览 + 入金 + 流水
└─ 我的       账户 / 设置 / 客服

顶部胶囊：UID + 激活状态 + 资产速览
左上角抽屉：多账户 / 切换语言 / 夜间模式（默认深色）
```

**H5 下单面板必须是全屏 Bottom Sheet**，不做桌面那种 300px 侧栏，因为拇指热区。

### 5.3 关键用户旅程（必须在 PRD 里画时序）

- J1. 新用户：**注册 → 激活墙 → 申请地址 → 入金 → 激活 → 首次下单**
- J2. 老用户：**登录 → 看盘 → 下单 → 持仓中调 TP/SL → 平仓**
- J3. 风险：**持仓浮亏扩大 → 保证金率下降 → 风险提示 → 强平预警 → 强平执行**
- J4. 异常：**行情源掉线 → stale 徽标 → 下单被拒 → 文案引导**
- J5. 冻结：**状态变 FROZEN → 交易禁用 → 仅可查看与提现（占位）**

---

## §6 页面清单与关键交互

按优先级 P0 / P1 / P2 标注。**P0 必须做，P1 尽量做，P2 占位就行**。

### Web 桌面

| # | 页面 | 优先级 | 关键区域 |
|---|---|---|---|
| W1 | 登录 / 注册 / 找回密码 | P0 | 渐变品牌侧栏 + 右侧表单 + 密码强度条 |
| W2 | 激活引导墙 | P0 | 大步骤进度（选链 → 充值 → 确认 → 激活）+ 实时状态 |
| W3 | 市场首页 | P0 | 品种分类 Tab + 热门 + 涨幅榜 + 搜索 + 迷你走势 |
| W4 | 交易终端 | P0 | 左：品种切换；中：K 线 + 深度；右：下单；下：持仓/订单 |
| W5 | 持仓详情弹层 | P0 | TP/SL 调整 + 强平价 + 浮盈亏可视化 |
| W6 | 钱包总览 | P0 | 资产构成环图 + 可用/保证金 + 快捷入金 |
| W7 | 入金（多链） | P0 | 选链 → 地址 + 二维码 → 跟踪确认 → 完成 |
| W8 | 账本 / 流水 | P1 | 表格 + 筛选（入金/手续费/盈亏/强平） |
| W9 | 订单中心 | P1 | 当前委托 / 历史委托 / 成交（有占位） |
| W10 | 账户中心 | P1 | 个人资料 / 安全 / 偏好 / 通知 |
| W11 | 帮助中心 | P2 | FAQ + 公告 |
| W12 | 提现 | P2 | "即将开放" |

### H5 / 移动

| # | 页面 | 优先级 |
|---|---|---|
| M1 | 登录 / 注册 | P0 |
| M2 | 激活引导 | P0 |
| M3 | 首页行情 | P0 |
| M4 | 行情列表（全量 + 搜索 + 收藏） | P0 |
| M5 | 交易详情 + 全屏 K 线 | P0 |
| M6 | 下单 Bottom Sheet | P0 |
| M7 | 持仓 Tab | P0 |
| M8 | 资产 | P0 |
| M9 | 入金流程 | P0 |
| M10 | 我的 | P1 |

---

## §7 视觉与组件规格（深色科技冷静）

### 7.1 Design Token（必须以 CSS 变量 + `tokens.json` 同时交付）

**色板（示例值，你可以微调但不要跑偏）：**

```
/* 背景 */
--bg-0: #07080C;        /* 最底层 */
--bg-1: #0D0F14;        /* 卡片底 */
--bg-2: #141823;        /* 卡片上浮 */
--bg-3: #1B2030;        /* 输入、hover */

/* 描边 */
--border-subtle: #1F2433;
--border-regular: #2A3142;
--border-strong: #3A4358;

/* 文字 */
--text-primary: #E6E9F2;
--text-secondary: #A6ADBF;
--text-tertiary: #6B7388;
--text-disabled: #3D4254;

/* 品牌 / 强调 */
--brand-primary: #3B82F6;      /* 电光蓝 */
--brand-hover:   #5FA0FF;
--brand-soft:    rgba(59,130,246,0.14);

/* 涨跌语义（不能用红绿色盲歧义的纯红纯绿） */
--up:    #22C55E;              /* 主涨 */
--up-soft: rgba(34,197,94,0.14);
--down:  #EF4444;              /* 主跌 */
--down-soft: rgba(239,68,68,0.14);

/* 风险等级 */
--risk-low:    #22C55E;
--risk-medium: #F59E0B;
--risk-high:   #EF4444;
--risk-liq:    #FF3B64;        /* 强平线专色 */

/* 状态 */
--warn:  #F59E0B;
--info:  #38BDF8;
--success: #22C55E;
--danger: #EF4444;

/* stale 行情专用灰 */
--stale: #5B6278;
```

**字体 / 字阶：**

- 文字主字体：`"Inter", "PingFang SC", system-ui, sans-serif`
- **数字专用等宽字体**：`"JetBrains Mono", "IBM Plex Mono", ui-monospace, monospace`（所有价格、数量、PnL 必须走此字体，避免跳字）
- 字阶：`11 / 12 / 13 / 14 / 16 / 18 / 20 / 24 / 32`
- 行高：正文 1.5、数字 1.2

**间距 / 圆角 / 阴影：**

- 间距：`2 / 4 / 6 / 8 / 12 / 16 / 20 / 24 / 32 / 40 / 48`
- 圆角：`--r-sm 4 / --r-md 8 / --r-lg 12 / --r-xl 16`
- 阴影：低存在感，`0 1px 0 rgba(255,255,255,0.02) inset, 0 8px 24px rgba(0,0,0,0.35)`

**动效：**

- 默认缓动 `cubic-bezier(0.2, 0.8, 0.2, 1)`
- 时长 120 / 180 / 240 ms 三档
- 涨跌闪烁：价格变化时做 240ms 的 `--up-soft / --down-soft` 背景闪烁，过 stale 阈值后停止

### 7.2 必须落地的组件库

**通用：**
- `Button`（primary / secondary / ghost / danger / long / short / 禁用状态）
- `Input`（含数字格式化、单位后缀、`max`/`减半`/`25%/50%/75%/100%` 滑块）
- `Select` / `Tabs` / `Tag` / `Tooltip` / `Modal` / `Drawer` / `BottomSheet`
- `Toast` / `Banner`（含错误码分治文案版）
- `Skeleton`（所有首屏数据都要骨架屏）

**行情与交易专用：**
- `PriceTicker`（数字 + 涨跌色 + 方向箭头 + stale 呼吸灯）
- `SparkLine`（24h 迷你走势）
- `KLineChart`（使用 SVG 或 `lightweight-charts`，含工具栏、指标、时间切换）
- `OrderBook`（最好同时有深度图叠加，可折叠）
- `OrderForm`（市价 Tab + 限价占位 Tab）
  - 杠杆滑块（1-100x），实时保证金预估
  - 数量滑块 + 百分比快捷
  - TP/SL 折叠区，带盈亏预估
  - stale 时按钮灰化 + 提示
  - 风险等级指示条（绿/黄/红，基于 `保证金率`）
- `PositionCard` / `PositionRow`
  - 方向徽章（多 / 空）
  - 开仓价 / 标记价 / 强平价（含强平价距离当前价的百分比警示）
  - TP/SL 快改（hover 出铅笔图标）
  - 浮盈亏动态变色
- `MarginRing`（保证金率圆环，绿 → 黄 → 红 → 强平）
- `RiskBar`（风险水位横条）
- `QuoteStalenessBadge`（stale 徽标）
- `DepositStepper`（入金 4 步走）
- `UserStatusPill`（`待入金 / 已激活 / 已冻结 / 已封禁`）
- `TraceIdTag`（可复制的 traceId，用于用户给客服）

### 7.3 交互铁律

- **所有破坏性操作**（下单、平仓、提现）必须有二次确认
- **所有数字闪变**不能让表格列宽跳动（用等宽字体 + 固定 `tabular-nums`）
- **骨架屏 → 实数**的切换要淡入，不能瞬切
- **空状态**都要有"为什么空 / 下一步建议"两行文案
- **错误态**要有 traceId 可复制（给客服用）
- **stale 状态**下的下单按钮必须**物理禁用** + 显示原因
- **WS 断线**必须有顶部 banner + 自动重连计数

---

## §8 交付物清单与目录结构

### 8.1 产出目录（在 FalconX 仓库下新建）

```
FalconX/
├─ docs/
│  └─ design/
│     ├─ README.md                        索引
│     ├─ 01-产品方案与信息架构.md           PRD 主文档
│     ├─ 02-视觉与设计系统.md               Token + 组件
│     ├─ 03-页面清单与交互规格.md            每页细节
│     ├─ 04-前端落地建议书.md                技术栈 + 目录 + 接口对接
│     └─ tokens.json                       可被前端工程直接 import
└─ prototype/
   ├─ index.html                          入口（导航桌面 / H5 / 组件库）
   ├─ assets/
   │  ├─ styles/tokens.css
   │  ├─ styles/base.css
   │  ├─ styles/components.css
   │  ├─ js/mock.js                       模拟行情 / 账户 / 持仓数据
   │  ├─ js/charts.js                     SVG 图表
   │  └─ fonts/                           Inter + JetBrains Mono（可用 CDN）
   ├─ desktop/
   │  ├─ login.html
   │  ├─ register.html
   │  ├─ activation.html                  激活引导墙
   │  ├─ markets.html                     市场首页
   │  ├─ trade.html                       交易终端（主场）
   │  ├─ wallet.html                      资产总览
   │  ├─ deposit.html                     多链入金
   │  ├─ orders.html                      订单中心
   │  ├─ account.html                     账户中心
   │  ├─ help.html                        帮助
   │  └─ components.html                  组件库 Showcase
   └─ mobile/
      ├─ login.html
      ├─ activation.html
      ├─ home.html                        首页行情
      ├─ markets.html                     行情列表
      ├─ trade.html                       交易详情 + 下单
      ├─ position.html                    持仓
      ├─ wallet.html
      ├─ deposit.html
      └─ me.html
```

### 8.2 每个文档必须包含

**01-产品方案与信息架构.md：**
- 目标与非目标
- 用户画像（3 个 persona）
- 关键旅程时序图（mermaid）
- IA 树（Web 桌面 / H5 各一份）
- 页面清单 + 优先级 + 依赖
- 指标（北极星 + 一级 + 漏斗）
- 上线后数据埋点建议

**02-视觉与设计系统.md：**
- 品牌定位一段话
- 色板 + 使用规则（禁止的搭配）
- 字体与字阶表
- 间距 / 圆角 / 阴影 / 动效
- 组件清单 + 每个组件的状态枚举（default / hover / active / disabled / loading / error）
- 涨跌语义使用规范
- 无障碍：对比度 AA、键盘可达

**03-页面清单与交互规格.md：**
- 每页：**目标 / 入口 / 数据字段 / 交互状态 / 异常态 / 埋点 / 对应后端接口**
- 每页画出 wireframe（mermaid `flowchart` 或 ASCII 框）
- 每页标出 P0/P1/P2 组件

**04-前端落地建议书.md：**
- 技术栈建议：`Vite + React 19 + TypeScript + Tailwind + shadcn/ui + TanStack Query + Zustand + react-hook-form + zod + lightweight-charts + bignumber.js + dayjs`
- 目录结构
- API Client 结构（统一响应体 `{code, message, data, traceId}` 的拦截器）
- WS Client 结构（订阅管理、重连、stale 识别）
- 错误码 → 文案 map 文件
- i18n（简中 / 繁中 / English）
- 环境变量
- 与 FalconX gateway 的联调 checklist

### 8.3 HTML 原型必须能做到

- 打开 `prototype/index.html` 能看到导航页（桌面 / H5 / 组件库）
- 桌面页面最低兼容 1440×900
- H5 页面默认 `375×812`（iPhone 13 基准）
- 不依赖构建工具，**双击 HTML 即可预览**
- 模拟数据放 `assets/js/mock.js`，不调真实后端
- K 线用 SVG 手绘若干根即可，不要求实时动
- 涨跌闪烁、stale 呼吸灯、骨架屏等**可视动效必须动起来**，不能只截屏

---

## §9 每一页的详细需求（P0 页面逐页展开）

> 为控制 prompt 长度，这里只展开最关键的 5 个 P0 页面。其余页面 Codex 应**按同一格式自行补齐**后放入 `03-页面清单与交互规格.md`。

### 9.1 W4 交易终端（重中之重）

**布局（桌面 12 栅格）：**

```
┌────────────────── 顶部导航（跨列） ──────────────────────┐
│  [栏] 品种头（代码 / 最新价 / 24h 涨跌 / 24h 量 / stale) │
├──[2]──┬──────[7]──────┬──────[3]──────────────────────┤
│ 品种  │               │  下单面板                     │
│ 列表  │   K 线 + 指标  │  ┌──────────────────────┐    │
│ 搜索  │   工具栏       │  │ 市价 / 限价(占位)     │    │
│ 收藏  │   时间周期切换  │  │ 方向：买/卖           │    │
│       │               │  │ 数量 + 百分比滑块       │    │
│       │               │  │ 杠杆滑块                │    │
│       │               │  │ 预估：保证金/手续费      │    │
│       │               │  │ TP/SL 折叠              │    │
│       │               │  │ [买入]   [卖出]         │    │
│       │               │  │ 风险等级条               │    │
│       │               │  └──────────────────────┘    │
├───────┴───────────────┴──────────────────────────────┤
│   订单簿 / 深度图（可切换） │  最近成交                │
├──────────────────────────────────────────────────────┤
│   [持仓(3)] [当前委托(0)] [历史委托] [成交] [资产流水] │
│                                                      │
│   持仓表：Side · Symbol · Qty · 开仓 · 标记 · 强平   │
│            · 浮盈亏 · TP/SL · 保证金 · 操作          │
└──────────────────────────────────────────────────────┘
```

**必须支持的交互：**
- 切品种：列表点击 → K 线 / 订单簿 / 下单面板全部切换 + WS 重新订阅（不要重连）
- K 线时间周期：`1m / 5m / 15m / 1h / 4h / 1d`（和后端 WS 对齐）
- 下单：
  - 输入数量实时算保证金占用、手续费、强平价预估
  - 杠杆变化实时重算
  - 可用保证金不足时 → 数量输入红边 + 快捷"减半"按钮
  - stale → 买卖按钮物理禁用 + 提示
- 持仓行操作：平仓（二次确认）/ 调 TP/SL（inline 小面板）/ 详情弹层
- 强平价离当前价 < 5%：该行整行变 `--risk-liq` 10% 透明度背景 + 铃铛

**数据字段对齐：**
- 品种头：`symbol / last (=mark) / 24hChangePct / 24hVolume / stale / tradingSession`
- K 线：`open/high/low/close/openTime/closeTime/isFinal`
- 持仓：`positionId / symbol / side / quantity / entryPrice / markPrice / unrealizedPnl / liquidationPrice / takeProfitPrice / stopLossPrice / quoteStale`

**异常态：**
- WS 断线：顶部 banner "行情推送已断开，正在重连（第 2 次）"
- stale：品种头 stale 徽标 + 下单按钮禁用 + "行情延迟，无法开新仓"
- `SYMBOL_TRADING_SUSPENDED`：下单区整块灰化 + "当前为休市时段 · 下一开盘时间：xxx"
- 用户状态非 `ACTIVE`：下单区锁定 + 跳"去激活"

### 9.2 W2 激活引导墙

**必须有一个巨大的 4 步 Stepper：**

`1. 选择充值网络 → 2. 获取地址 → 3. 链上发送 → 4. 自动激活`

- 步骤 3 → 4 的跳转由轮询 `/api/v1/trading/accounts/me`（看 `balance > 0`）或监听推送（后续接入）触发
- 步骤 3 页面必须有：地址 + 二维码 + 复制按钮 + 最小入金金额提示 + 预计到账时间
- 所有页面顶部保留"跳过，先去看行情"次级入口，避免强制锁死（但交易功能真锁）

### 9.3 W7 多链入金

**不要搞得太花，零售用户看得懂就行。**

- 左：链选择器（BTC / ETH / TRON / Solana / BSC / Polygon 等，按 wallet-service 实际支持范围）
- 右上：当前用户该链的唯一充值地址 + 二维码
- 右下：最近充值记录（`t_wallet_deposit_tx` 视图：hash / 确认数 / 状态 / 时间）
- 风险提示条（橙色）：
  - "仅支持 USDT / USDC 稳定币，误充其他代币无法找回"
  - "最少 N 个确认后到账"
  - "请勿通过交易所内部转账"

### 9.4 W5 持仓详情弹层

- 左：实时浮盈亏大数字 + 迷你走势（持仓持有期间）
- 右上：调 TP / SL（两个价格输入 + 盈亏预估 + "按百分比"快捷 5% / 10%）
- 右中：保证金率圆环 + 强平价 + 距离强平 %
- 右下：[平仓] [调 TP/SL] [加仓占位]
- 底部：持仓操作日志（开仓、调 TP/SL、部分平仓）

### 9.5 M6 H5 下单 Bottom Sheet

- 从底部滑出占 70% 高
- 顶部：品种 + 最新价 + stale 徽标
- 中部：大号"买入 / 卖出"分段控件
- 数量 / 杠杆 / TP / SL 分块
- 保证金预估条
- 底部固定栏："确认下单"大按钮（48px 高 + 品牌色）
- 下单成功后底部换成"查看持仓 / 继续下单"两个按钮

---

## §10 验收标准（Definition of Done）

你的产出**必须满足全部**这些条目才算完成：

**功能完整性：**
- [ ] `docs/design/` 下 5 个 Markdown 文件齐全
- [ ] `docs/design/tokens.json` 可被 import（数组 / 对象结构）
- [ ] `prototype/index.html` 打开能导航到所有桌面与 H5 页面
- [ ] 桌面 12 个页面（W1–W12）全部存在；H5 10 个页面（M1–M10）全部存在
- [ ] `components.html` 展示所有组件的 default / hover / disabled / loading / error 状态

**契约合规：**
- [ ] 页面中展示的字段名、错误码、状态名**完全来自** FalconX 规范（不得自创）
- [ ] stale 行情视觉告警在 `trade.html` 和 `markets.html` 都能看到
- [ ] 市价单表单字段与 `/api/v1/trading/orders/market` 完全对齐
- [ ] 下单按钮禁用逻辑覆盖：用户非 ACTIVE / stale / 保证金不足 / 非交易时段
- [ ] 未实现能力（限价单 / 平仓 REST / 提现）都是占位态，文案写"开发中"

**视觉与体验：**
- [ ] 深色主题对比度 AA（文字对背景 ≥ 4.5:1）
- [ ] 所有数字使用等宽字体
- [ ] 涨跌色全程一致（不能出现某页绿涨红跌，另一页绿跌红涨）
- [ ] 所有数据块都有骨架屏
- [ ] 所有错误都能复制 traceId

**工程质量：**
- [ ] HTML 原型双击可预览，无 404
- [ ] `tokens.css` 变量与 `tokens.json` 完全一致
- [ ] 不依赖 Node 构建
- [ ] 代码能通过浏览器控制台无报错

**文档质量：**
- [ ] 每份文档首页有"本文件和 §X 文档的关系"导航
- [ ] 所有外部引用的 FalconX 规范都用**相对路径链接**
- [ ] 所有被阉割 / 占位的能力都在 `04-前端落地建议书.md` 的"待后端就绪后补齐清单"里列出

---

## §11 执行顺序与阶段

**Codex 请严格按阶段推进，一个阶段没完成不得跳下一步：**

- **Stage A：读规范 + 输出摘要**（§2 全读完，用 300 字说掌握了什么关键约束）
- **Stage B：产出 `01-产品方案与信息架构.md`**（让我先看方向是否对）
- **Stage C：产出 `02-视觉与设计系统.md` + `tokens.json` + `tokens.css`**
- **Stage D：产出 `components.html` 组件库**（先让组件扎实）
- **Stage E：桌面 P0 页面**（W1 W2 W3 W4 W5 W6 W7）
- **Stage F：H5 P0 页面**（M1 M2 M3 M4 M5 M6 M7 M8 M9）
- **Stage G：P1 / P2 页面补齐**
- **Stage H：`03-页面清单与交互规格.md` + `04-前端落地建议书.md`**
- **Stage I：自检清单过一遍（§10）+ 修正**

每个 Stage 完成后**用一段简短的中文"阶段结论"**告知我，我来放行。

---

## §12 红线约束（违反任何一条都不可接受）

1. **不改后端**。任何让前端看起来更舒服但要求改后端契约的建议，都只能写进"后续建议"，不得直接落地。
2. **不改 FalconX CLAUDE.md 的任何约束**。尤其是"不允许把业务 DTO、Entity、Mapper 上收到 common / infrastructure"这类后端规则，在你写"04-前端落地建议书"时不要越界到后端目录建议。
3. **不许虚构接口 / 错误码 / 状态名**。只能用 `FalconX统一接口文档.md` 和 `状态机规范.md` 里已有的。
4. **不许用 Node 构建**。HTML 原型必须纯静态、双击可开。
5. **不许用 localStorage / sessionStorage 储存敏感数据**。原型里的 mock 数据用 JS 常量或 `window.__MOCK__`。
6. **不许在 UI 上做任何暗示"保本 / 稳赚 / 无风险"**的文案。CFD 行业合规红线。
7. **不许把 traceId 当幂等键**或展示为可编辑（只读可复制）。
8. **不许在一个页面里同时出现两套涨跌色**。
9. **不许让 stale 行情状态下的下单按钮仅靠视觉灰化**——必须 `disabled` 物理禁用。
10. **不许把"未实现能力"画得像已实现**——要显式"开发中"徽标。

---

## 启动指令

Codex，你现在的入口是 `FalconX/` 仓库根目录。请按 §11 的顺序开始执行。

第一步：进入 Stage A，读完 §2 列出的全部文档，用 300 字中文摘要告诉我你掌握到的关键约束。不要开始动代码，等我看完摘要放行后再进 Stage B。

开始。
