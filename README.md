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

若本机网络经过代理/自签根证书，调用 OCR 会报 `PKIX path building failed`，启动时加 `-Djavax.net.ssl.trustStoreType=KeychainStore -Djavax.net.ssl.trustStore=NONE`（macOS 钥匙串）。

OCR 默认走阿里通义（`AB_OCR_MODEL`，默认见 `application.yml`）：`export AB_OCR_API_KEY=<百炼 key>` 即可，密钥不要写进仓库文件；该 key 所在工作空间必须已开通所选视觉模型，否则返回 `Model not exist` / `Model.AccessDenied`；未配置 key 时使用固定的模拟数据（不读取图片）；配置方式、Nacos、测试命令见 [quickstart](specs/001-receipt-price-tracker/quickstart.md)。

## 模块

```
ab-common ← ab-merchant / ab-product / ab-ocr / ab-user ← ab-family ← ab-receipt ← ab-analytics ← ab-app
```

| 模块 | 职责 | 表前缀 |
|---|---|---|
| ab-common | 配置、SQLite/Flyway、金额、Web 异常 | - |
| ab-merchant | 商户（国家/城市/商圈/类型/经纬度） | mer_ |
| ab-product | 商品（品牌/规格/标签） | prd_ |
| ab-ocr | 识别 SPI（llm / mock / noop） | - |
| ab-user | 用户、会话、手机/Apple/Google/微信/QQ/LINE 登录、登录定位 | usr_ |
| ab-family | 家庭：创建/加入/退出/解散，成员共享消费 | fam_ |
| ab-receipt | 小票录入与存储（按用户归属） | rcp_ |
| ab-analytics | 价格事实表 + 日汇总 + 图表查询（ETag + 缓存） | ana_ |
| ab-app | 装配、静态前端、集成测试 | - |

模块间只能通过 `.api` 包交互，由 ArchUnit 强制；各模块独立迁移脚本，可单独拆出为服务。

## 登录与家庭

- 所有 `/api/**`（除 `/api/auth/**`、`/api/meta`）需要登录。登录方式：手机验证码、Apple / Google / 微信 / QQ / LINE 授权；登录页默认手机号 + 国家区号（默认中国 +86，仅当定位落在其他国家时才自动切换）。定位为尽力而为：成功则记录经纬度与时间，失败/拒绝则跳过，不影响登录（浏览器定位需 HTTPS 或 localhost；`AB_AUTH_REQUIRE_LOCATION=true` 可改为必须定位）。「我的」页可修改资料、退出登录或注销账号（`DELETE /api/me`，永久删除用户、登录记录与全部小票）。
- 一个用户至多加入一个家庭；成员可切换「我的 / 家庭」查看整个家庭的小票与价格统计，但只能修改自己的记录。
- 本地开发：`AB_AUTH_DEV` 默认 `true`，验证码直接在接口/页面返回，未配置凭据的第三方登录使用演示账号。**对外部署必须设置 `AB_AUTH_DEV=false`**，并清空固定验证码 `AB_SMS_FIXED_CODE=`（默认 `000000`，对接短信平台前的临时方案，任何人可用它登录任意手机号），并配置 `ab.auth.oauth.<provider>.client-id/client-secret`（Apple 另需 team-id/key-id/private-key）与 `AB_PUBLIC_BASE_URL`；短信需提供自己的 `SmsSender` 实现（默认只写日志）。

## 文档

设计文档在 [`specs/001-receipt-price-tracker/`](specs/001-receipt-price-tracker/)（spec / plan / data-model / contracts / tasks）与 [`specs/002-user-family/`](specs/002-user-family/)（登录与家庭），开发规范见 [`.specify/memory/constitution.md`](.specify/memory/constitution.md)。
