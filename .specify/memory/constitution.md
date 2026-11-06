# Account Book（小票记账 / 价格比较）Constitution

## Core Principles

### I. 模块自治，边界即契约（NON-NEGOTIABLE）
- 后端采用 Maven 多模块，每个业务域一个模块：`ab-merchant`、`ab-product`、`ab-ocr`、`ab-receipt`、`ab-analytics`；`ab-common` 仅放与业务无关的共享内核；`ab-app` 只负责装配（启动类、配置、静态前端、全局测试），不含业务逻辑。
- 每个业务模块的包结构固定为 `com.accountbook.<module>.api`（对外：接口、DTO、领域事件）与 `com.accountbook.<module>.internal`（对内：controller、service、repository、实现）。**其他模块只允许依赖 `api` 包**，由 ArchUnit 测试（`ab-app` 的 `ModuleBoundaryTest`）强制执行，违反即构建失败。
- 模块依赖必须是有向无环图：`common ← merchant / product / ocr ← receipt ← analytics`。禁止反向与循环依赖。
- 每个模块**独占**自己的表（表名前缀：`mer_` `prd_` `rcp_` `ana_`），禁止跨模块 JOIN、禁止跨模块外键；跨模块数据只能通过对方 `api` 接口或领域事件获取。
- 每个模块自带 Flyway 迁移脚本（`classpath:db/migration/<module>`）和独立的 history 表，以便日后整体搬走/拆成独立服务。

### II. 事件驱动的读写分离
- 写模型（merchant/product/receipt）在事务内发布不可变领域事件（`record`，位于发布方 `api.event` 包）；读模型（analytics）订阅事件维护自己的投影表。监听器必须**幂等**（按业务主键 upsert / 先删后插）。
- 事件当前由 Spring `ApplicationEventPublisher` 同步分发（与写入同事务、强一致）；监听接口与事件载荷按"将来可换成消息队列"的标准设计：只含 ID 与快照值，不含 ORM 对象，可 JSON 序列化。

### III. 图表读取性能优先
- 图表/统计接口**禁止**直接扫描明细表做实时聚合。必须读取预聚合表（`ana_price_daily` 等）或带覆盖索引的事实表（`ana_price_fact`）。
- 聚合表在事件监听中按受影响桶（bucket）重算，保证 min/max 在删除/修改后仍然正确。
- 图表接口返回紧凑的列式 JSON（`labels[]` + `series[].data[]`），并带 `ETag`（由数据版本号生成）+ `Cache-Control`；服务端用 Caffeine 缓存查询结果，数据版本变化时整体失效。
- 新增统计维度前必须先在 spec 里写明查询形态与索引设计。

### IV. 配置：Nacos 优先，本地必可启动
- 配置来源优先级：Nacos 配置中心 > 本地 `application.yml`。**本地没有 Nacos 时必须零报错回退并正常启动**（`optional:` 导入 + 短超时 + 启动探测）。
- Nacos 缺席不得影响 `/actuator/health`（已关闭 `management.health.nacos-config`），也不得拖慢启动。
- 任何需要环境差异的值（OCR key、数据目录、缓存大小等）必须出现在 `application.yml` 的默认值中，Nacos 仅做覆盖。禁止把密钥写进仓库；密钥通过环境变量或 Nacos 注入。

### V. SQLite 使用规范
- 数据库只有一个 SQLite 文件，启用 WAL（`journal_mode=WAL`、`synchronous=NORMAL`、`foreign_keys=ON`、`busy_timeout`）。
- 金额一律以**最小货币单位的整数**存储（`*_minor`，按 `java.util.Currency` 的小数位数换算），API 层使用十进制数；禁止使用浮点数做金额运算。
- 时间：`purchased_at` 存商户所在地的本地时间（ISO-8601 无时区），并冗余 `purchased_date`（`yyyy-MM-dd`）用于索引；系统时间戳字段（`created_at`/`updated_at`）存 UTC epoch 毫秒。
- 写事务尽量短；批量写入使用单事务；所有查询使用参数绑定，动态 `GROUP BY` / `ORDER BY` 只能走白名单映射。
- 连接串使用 `transaction_mode=IMMEDIATE`，因此**禁止使用 `@Transactional(readOnly = true)`**（SQLite 驱动不支持只读事务切换）；纯读取方法不加事务注解。
- 读可空列一律使用 `com.accountbook.common.db.Rows.nullableLong/nullableDouble`，**禁止** `(Long) rs.getObject(..)` 这类强转：驱动会按存储值返回 `Integer`/`Long`/`Double`，强转会在运行时抛 `ClassCastException`。

### VI. 前端规范：原生 ES Modules，零构建
- 前端为 H5 单页应用，位于 `ab-app/src/main/resources/static`，**不得引入构建步骤**（无 npm/webpack/vite）；第三方库禁止通过 CDN 引入（需离线可用），图表使用自研轻量 SVG 组件。
- 三种语言：`zh-CN`、`ja`、`en`。所有可见文案必须走 i18n 字典，**三个字典的 key 必须一致**（由测试脚本校验）；日期、金额用 `Intl` 按语言/币种格式化。
- 移动端优先（触控目标 ≥ 44px、安全区适配、拍照用 `<input type=file accept=image/* capture>`），上传前在客户端压缩图片。
- 页面 = 一个 ES 模块（`js/pages/*.js`）导出 `render(ctx)`；与后端的交互统一走 `js/api.js`；禁止在页面里直接写 `fetch`。

### VII. 测试与质量门禁
- 每个业务模块必须有：服务层单元/集成测试（使用临时 SQLite 文件，不 mock 数据库）。
- `ab-app` 必须有：模块边界测试（ArchUnit）、"录入小票→图表可查询"的端到端集成测试、i18n key 一致性检查。
- 合并前必须通过 `mvn -q verify`。新增 REST 接口必须同步更新 `specs/<feature>/contracts/`。
- 日志使用 SLF4J，禁止 `System.out`；对外错误统一为 `{code, message, details}` 并按 `Accept-Language` 本地化。

### VIII. 简单优先（YAGNI）与可演进
- 不引入 ORM（使用 Spring `JdbcClient` + 显式 SQL）、不引入与当前需求无关的框架。
- OCR 通过 `OcrProvider` SPI 接入（`llm` / `mock` / `noop`），新增识别后端只需新增实现，不改 receipt 模块。

## 开发流程（Spec-Driven）

1. 新功能先走 `/speckit-specify` → `/speckit-plan` → `/speckit-tasks` → `/speckit-implement`；文档位于 `specs/<NNN-feature>/`。
2. 代码索引使用 CodeGraph（仓库根的 `.codegraph/`）：理解/定位代码优先用 `codegraph explore`，再用 grep/Read；大改后执行 `codegraph sync`。
3. 提交粒度：一个提交只做一件事；提交信息说明"为什么"。

## Governance

本宪章高于其他开发约定。修改须：说明动机 → 更新版本号 → 同步更新受影响的 spec/plan 与 ArchUnit/i18n 校验。所有 PR/评审必须核对：模块边界、金额类型、图表查询是否走预聚合、i18n 三语言完整。

**Version**: 1.1.1 | **Ratified**: 2026-11-06 | **Last Amended**: 2026-11-06
（1.1.0：新增 SQLite 禁用只读事务、可空列读取规范、Nacos 缺席不影响健康检查。）
（1.1.1：支持语言由四种改为三种，移除 zh-TW。）
