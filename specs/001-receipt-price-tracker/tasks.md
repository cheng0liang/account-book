# Tasks 001

图例：[x] 完成　[ ] 待办　[P] 可并行

## Phase 1 骨架
- [x] T001 Maven 父 POM 与 7 个子模块（BOM 固定版本）
- [x] T002 ab-common：错误体、i18n 消息、金额工具、`ModuleSchema`/`ModuleMigrator`、`Rows`、`NameNormalizer`、TestDb（test-jar）
- [x] T003 ab-app：数据源（WAL/IMMEDIATE）、Nacos 可选导入与回退、health 配置
- [x] T004 spec-kit 宪章 `.specify/memory/constitution.md`（v1.1.0）

## Phase 2 业务模块（US2）
- [x] T010 [P] ab-merchant：迁移、仓储、服务、REST、`MerchantChangedEvent`
- [x] T011 [P] ab-product：迁移、规格解析、标签、品牌联想、REST、`ProductChangedEvent`
- [x] T012 [P] ab-ocr：SPI + llm/mock/noop + 自动选择
- [x] T013 ab-receipt：识别编排（商户/商品匹配、警告）、CRUD、图片存储、事件
- [x] T014 ab-analytics：迁移、投影监听、日汇总、ETag/Caffeine、重建

## Phase 3 查询与图表（US3/US4）
- [x] T020 facets / price-trend / price-compare / price-table / spending 接口
- [x] T021 标准单位价（100g/100ml/件）
- [x] T022 契约文档 `contracts/api.md`

## Phase 4 前端（US1/US5）
- [x] T030 路由、i18n（3 语言）、api.js、Zaim 风格主题
- [x] T031 扫描页：拍照/选图、客户端压缩、可编辑表单、警告展示
- [x] T032 记录页、详情/编辑/删除
- [x] T033 分析页：自研 SVG 折线/柱状/环形图、过滤器
- [x] T034 首页本月汇总

## Phase 5 质量
- [x] T040 ArchUnit 模块边界（12 条）
- [x] T041 端到端 `ReceiptFlowTest`（含 Nacos 死端口回退、ETag/304、可空列回归）
- [x] T042 i18n key 一致性检查脚本（`i18n-check.mjs`）
- [x] T043 浏览器联调（打包 jar、无 Nacos、三语言、无 console error）
- [x] T044 修复：可空列强转 `ClassCastException`（`Rows`）、Nacos health DOWN、i18n 警告 key
- [x] T045 [P] 模块级服务测试：merchant / product / receipt / analytics（临时 SQLite，不 mock）
- [x] T046 最终 `mvn verify`

## Phase 6 工程化
- [x] T050 spec-kit 文档（spec/plan/data-model/quickstart/tasks/contracts）
- [x] T051 项目 CLAUDE.md（构建环境、模块规则、SQLite 约定、前端零构建）
- [x] T052 README
- [x] T053 CodeGraph 索引（`codegraph init`）
- [ ] T054 `.gitignore`（需用户自行添加：target/ data/ *.db* .idea/ .codegraph/ .playwright-mcp/ .DS_Store）

## 后续（Backlog）
- 分析页"对比"首次进入给出默认筛选（取购买次数最多的商品）
- 商户地图/附近商户（经纬度已存）
- 导出/导入 CSV；条码扫描
- analytics 迁出为独立服务（事件换 MQ，读库独立）
