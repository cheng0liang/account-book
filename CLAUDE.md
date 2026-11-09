# account-book

Java 17 + Spring Boot 3.3 多模块 Maven 项目，SQLite（WAL），前端为无构建的原生 ES Modules H5。规范以 `.specify/memory/constitution.md` 为准，设计见 `specs/001-receipt-price-tracker/`。

## 构建与测试

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/open-jdk-17.0.12.0
export MAVEN_OPTS="-Djavax.net.ssl.trustStoreType=KeychainStore -Djavax.net.ssl.trustStore=NONE"
mvn -s /tmp/ab-settings.xml -o clean install     # 离线 + 本机设置；无该 settings 时去掉 -s/-o
node ab-app/src/test/frontend/i18n-check.mjs     # 三语言 key 一致 + JS 语法检查
java -jar ab-app/target/account-book.jar         # :8080；本机代理环境调 OCR 需加 MAVEN_OPTS 里同样的 -Djavax.net.ssl.trustStore* 参数，否则 PKIX 报错
```

- 打 ab-app 必须 `clean package`，否则会留下旧的 repackage jar。
- `mvn -q` 仍会输出 Flyway/Hikari 的 INFO，判断结果看 `*/target/surefire-reports/*.xml`。
- 改了某模块后，依赖它的模块要 `install`（或 `-am`），否则读到本地仓库里的旧 jar。

## 模块规则

- DAG：common ← merchant/product/ocr/user ← family ← receipt ← analytics，ab-app 装配。
- 跨模块只依赖对方的 `.api` 包；`.internal` 不可见。`ModuleBoundaryTest`（ArchUnit）强制，改动后必须保持通过。
- 表前缀 `mer_/prd_/rcp_/ana_/usr_/fam_`，禁止跨模块外键与 JOIN；跨模块数据靠 API 或同步 Spring 事件（在写入方事务内）。
- 迁移脚本在 `classpath:db/migration/<module>`，由 `ModuleMigrator` 按模块执行，历史表 `flyway_<module>`；`spring.flyway.enabled=false`。
- 新增业务模块要在 `ModuleBoundaryTest` 和 `specs/.../plan.md` 的模块表里登记。

## SQLite / 数据访问

- 用 `JdbcClient` 写显式 SQL，不用 ORM。
- 连接 `transaction_mode=IMMEDIATE`，**禁止 `@Transactional(readOnly = true)`**。
- 读可空列用 `Rows.nullableLong/nullableDouble`；SQLite 驱动的 `getObject` 会按存储值返回 Integer/Long/Double，直接强转会 ClassCastException。
- 金额存最小货币单位整数（`Money.toMinor/fromMinor`）；`Money.isValidCurrency` 会拒绝 XXX 等伪货币。
- 名称匹配统一走 `NameNormalizer`。
- 分析投影处理器必须幂等；改投影逻辑要把 `ProjectionService.REVISION` +1，启动时会自动重建。
- 图表接口 ETag 为 `"v{version}-{hash}"`，任何写投影的路径都要 `bumpVersion()`。

## 登录 / 家庭

- `/api/**` 除 `/api/auth/**`、`/api/meta` 外均需 Bearer 会话；用 `CurrentUser.id()`，不信任请求里的 userId。
- 小票/分析行带 `user_id`（0=遗留，首个注册用户认领）；读取用 `Scope.parse(p).userIds(familyApi, uid)`，分析缓存键/ETag 含 uid 列表；改用户相关投影逻辑要 bump `ProjectionService.REVISION`。
- 改小票仅限本人（403 `receipt.ownerOnly`），同家庭可读，其他人 404。
- `ab.auth.dev-mode` 开启时验证码回显 + 第三方 mock；生产必须关。第三方真实流程与短信网关无法在本地验证。
- 固定验证码 `AB_SMS_FIXED_CODE`（默认 `000000`，仅临时）；生产必须置空。定位 `AB_AUTH_REQUIRE_LOCATION` 默认 false（尽力而为，失败不阻断登录）。
- 注销账号 `DELETE /api/me`：`UserService.deleteAccount` 在事务内发布 `UserDeletedEvent`，family/receipt 监听并清理自己的数据（有其他成员的家庭创建者 409 `family.ownerCannotDelete`）；新增含用户数据的模块必须监听该事件。
- 前端：`auth.js` 存会话，`scope.js` 存家庭与范围；`<img>` 带不了 Authorization，图片走 `api.fetchBlobUrl`。

## 前端

- 不引入构建工具、打包器或 npm 依赖，源码在 `ab-app/src/main/resources/static/`。
- 新增文案必须同时加到 3 个语言字典（zh-CN/ja/en），用 i18n 脚本校验。
- 后端校验错误码在前端有对应的 `validation.*` / `warning.*` key。

## 配置

`AB_DATA_DIR`、`PORT`、`AB_OCR_API_KEY/BASE_URL/MODEL`、`NACOS_ADDR/NAMESPACE/GROUP/USERNAME/PASSWORD/ENABLED`；Nacos dataId 为 `account-book.yml`。Nacos 不可达不得影响启动和 `/actuator/health`。

## 代码索引

仓库已用 CodeGraph 建索引（`.codegraph/`）。查找/理解代码先用 `codegraph_explore`，大改后 `codegraph sync`。

## 其它约定

- 不要提交或推送，除非用户明确要求。
- `.gitignore` 由用户维护。
- 浏览器联调时 Playwright 的 route mock 会残留，切换场景前 `context.unrouteAll`；截图/文件路径需在仓库或 `.playwright-mcp` 下。
