# Implementation Plan 001：小票识别记账 + 价格比较

**Spec**: [spec.md](spec.md) | **Contracts**: [contracts/api.md](contracts/api.md) | **Data**: [data-model.md](data-model.md)

## 1. 技术栈
Java 17 · Spring Boot 3.3.5 · Spring Cloud 2023.0.3 / Alibaba 2023.0.1.3（Nacos Config）· sqlite-jdbc 3.46.1.3 · Flyway（按模块）· Caffeine · ArchUnit 1.3.0 · 前端原生 ES Modules。

## 2. 模块与依赖（DAG）
```
ab-common ◄── ab-merchant ◄──┐
          ◄── ab-product  ◄──┤
          ◄── ab-ocr     ◄───┼── ab-receipt ◄── ab-analytics
          ◄── ab-user ◄ ab-family ◄┘                 ▲
                                                     │
ab-app（装配：启动类 / application.yml / static 前端 / 全局测试）
```
| 模块 | 职责 | 表前缀 | 对外 `api` |
|---|---|---|---|
| ab-common | 异常/错误体、i18n 消息、金额工具、`ModuleSchema`+`ModuleMigrator`、`Rows`、`NameNormalizer`、TestDb | – | – |
| ab-merchant | 商户 CRUD 与查询 | `mer_` | `MerchantApi`、`MerchantChangedEvent` |
| ab-product | 商品、品牌、标签、规格解析 | `prd_` | `ProductApi`、`ProductChangedEvent` |
| ab-ocr | `OcrProvider` SPI：llm / mock / noop | – | `OcrApi`、`OcrResult` |
| ab-user | 用户、会话、短信/第三方登录、登录定位、授权账号（详见 002） | `usr_` | `UserApi`、`CurrentUser`、`UserRegisteredEvent` |
| ab-family | 家庭、成员、邀请码（详见 002） | `fam_` | `FamilyApi`、`Scope` |
| ab-receipt | 识别草稿编排、小票 CRUD、图片存储 | `rcp_` | `ReceiptApi`、`ReceiptSavedEvent`、`ReceiptDeletedEvent` |
| ab-analytics | 投影 + 图表/统计查询 + ETag/缓存 | `ana_` | `AnalyticsApi`（仅内部使用，无外部依赖者） |

边界规则（ArchUnit，`ModuleBoundaryTest` 12 条）：只能依赖他模块 `api` 包；无环；表前缀；`ab-common` 不依赖业务模块。

## 3. 数据库与事务
- 单文件 SQLite，连接串 `journal_mode=WAL&synchronous=NORMAL&foreign_keys=true&transaction_mode=IMMEDIATE&busy_timeout=N`。
- 写事务用 `@Transactional`（IMMEDIATE，避免读升级写死锁）；**不使用 readOnly**。
- Flyway 每模块独立：`classpath:db/migration/<module>`，history 表 `flyway_<module>`，`baselineOnMigrate`；`spring.flyway.enabled=false` 关闭全局自动配置，由 `ModuleMigrator` 按依赖顺序执行。
- 读取使用 `Rows.nullableLong/nullableDouble`（驱动 `getObject` 类型不稳定）。

## 4. 事件与投影（读写分离）
```
POST/PUT/DELETE receipt ──tx──► rcp_* 写入 ──publish──► ReceiptSavedEvent / ReceiptDeletedEvent
                                                         │ (同步、同事务)
                         ┌───────────────────────────────┘
                         ▼
              ana 投影监听器（幂等）
                1. 删除该 receipt_id 的旧 fact
                2. 写入新 fact（unit_minor = 净额/数量）
                3. 重算受影响桶 (date, merchant, product) 到 ana_price_daily
                4. ana_meta.version++
MerchantChangedEvent / ProductChangedEvent ──► 刷新 ana_*_dim，version++
```
- 启动时若 `ana_meta.projection_rev != REVISION` 自动全量重建；`POST /api/analytics/rebuild` 手动重建。
- 事件载荷为 record，仅含 ID 与快照值，可迁移到 MQ。

## 5. 图表读取路径（性能关键）
1. Controller 计算缓存 key（路径 + 规范化参数 + 语言无关）；
2. 带 `If-None-Match` 且等于当前 `"v{version}-{hash}"` → 直接 304（不查库）；
3. Caffeine 命中（容量 512）→ 直接返回缓存的 JSON bytes；
4. 未命中 → 读 `ana_price_daily`（+ 维表 join，均在 analytics 内部）→ 序列化 → 放缓存。
- `version` 变化时整体失效；响应 `Cache-Control: no-cache, private`（每次校验 ETag，但命中 304 近乎零成本）。
- 动态维度/排序通过白名单枚举映射到列名，不拼接用户输入。
- 标准单位价：`unit_minor / spec_base_qty * 100`（g/ml）或 `* 1`（件）；仅对有 `spec_dim` 的商品计算。

## 6. OCR SPI
`OcrProvider { String id(); boolean available(); OcrResult recognize(byte[] image, String mime, Locale hint) }`
- `llm`：调用兼容 OpenAI 视觉接口的模型（`ab.ocr.llm.*`：base-url / api-key / model），要求返回固定 JSON，服务端容错解析。
- `mock`：返回固定样例（带 `ocr_mock_data` 警告），保证无 key 可演示。
- `noop`：返回空草稿。
- `ab.ocr.provider=auto`：有 api-key → llm，否则 mock。

## 7. 前端结构
```
static/
  index.html            入口，唯一 <script type="module" src="js/app.js">
  css/                  移动端优先，Zaim 风格（底部 Tab、卡片、绿色主题）
  js/app.js             路由（hash）、i18n 初始化、Tab 栏
  js/api.js             所有 fetch 的唯一入口（错误体解析、Accept-Language）
  js/i18n/{zh-CN,ja,en}.js
  js/pages/*.js         home / scan / receipt-form / library / analytics / settings
  js/components/*.js    自研 SVG 图表（折线/柱状/环形）、对话框、选择器
  js/util/*.js          图片压缩（canvas）、格式化（Intl）
```
页面 = 一个 ES 模块，导出 `render(ctx)`。三语言 key 一致性由 `i18n-check.mjs` 校验。

## 8. 配置（application.yml 默认 → Nacos 覆盖）
- `spring.config.import=optional:nacos:account-book.yml`，`spring.cloud.nacos.config.timeout=2000`，`import-check.enabled=false`。
- `ab.data-dir`（`AB_DATA_DIR`）、`ab.db.*`、`ab.ocr.*`（`AB_OCR_API_KEY/BASE_URL/MODEL`）、`ab.default-currency`、`ab.currencies`；Nacos：`NACOS_ADDR` 等，dataId `account-book.yml`。
- `management.health.nacos-config.enabled=false`，仅暴露 `health,info`。

## 9. 测试策略
| 层 | 内容 |
|---|---|
| 单元/集成（模块内） | 临时 SQLite 文件 + `TestDb`（ab-common test-jar），不 mock 数据库 |
| 架构 | `ModuleBoundaryTest`（ArchUnit） |
| 端到端 | `ReceiptFlowTest`：识别→保存→比较→ETag/304→编辑→删除；Nacos 指向死端口验证回退 |
| i18n | `ab-app/src/test/frontend/i18n-check.mjs`（Node 脚本）：三语言 key 集合一致、枚举/警告码均有文案、代码中引用的 key 均存在 |
| 单元 | `MoneyTest`、`ReceiptJsonParserTest`（其余模块服务测试见 tasks T045） |
| 手动 | 浏览器联调（Playwright）：三语言切换、扫描→保存、图表、无 console error |

## 10. 风险
| 风险 | 缓解 |
|---|---|
| SQLite 单写者 | WAL + busy_timeout + 短事务；单用户场景足够；如需扩展，analytics 可迁出 |
| OCR 识别质量 | 草稿全可编辑；警告提示；提供商可插拔 |
| 投影与源数据不一致 | 幂等监听 + revision 自动重建 + 手动 rebuild |
| 驱动类型差异 | `Rows` 统一读取 + 回归测试 |
