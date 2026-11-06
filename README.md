# account-book

小票记账与比价：拍照/选图 → OCR → 可编辑表单 → 保存；按商户、商品、时间维度比价并出图表。

- 后端：Java 17 + Spring Boot 3.3，SQLite（WAL），无需额外数据库
- 前端：H5 单页应用（原生 ES Modules，无构建步骤），内置于后端静态资源
- 语言：简体中文 / 日本語 / English
- 配置：Nacos 配置中心，不可达时自动回退 `application.yml`

## 快速开始

```bash
mvn -pl ab-app -am clean package
java -jar ab-app/target/account-book.jar      # http://localhost:8080
```

不配置 OCR key 时使用模拟识别；配置方式、Nacos、测试命令见 [quickstart](specs/001-receipt-price-tracker/quickstart.md)。

## 模块

```
ab-common ← ab-merchant / ab-product / ab-ocr ← ab-receipt ← ab-analytics ← ab-app
```

| 模块 | 职责 | 表前缀 |
|---|---|---|
| ab-common | 配置、SQLite/Flyway、金额、Web 异常 | - |
| ab-merchant | 商户（国家/城市/商圈/类型/经纬度） | mer_ |
| ab-product | 商品（品牌/规格/标签） | prd_ |
| ab-ocr | 识别 SPI（llm / mock / noop） | - |
| ab-receipt | 小票录入与存储 | rcp_ |
| ab-analytics | 价格事实表 + 日汇总 + 图表查询（ETag + 缓存） | ana_ |
| ab-app | 装配、静态前端、集成测试 | - |

模块间只能通过 `.api` 包交互，由 ArchUnit 强制；各模块独立迁移脚本，可单独拆出为服务。

## 文档

设计文档在 [`specs/001-receipt-price-tracker/`](specs/001-receipt-price-tracker/)（spec / plan / data-model / contracts / tasks），开发规范见 [`.specify/memory/constitution.md`](.specify/memory/constitution.md)。
