# 实现计划

## 模块
| 模块 | 职责 | 表前缀 | 依赖 |
|---|---|---|---|
| ab-user | 用户、会话、短信/OAuth 登录、定位、授权账号 | `usr_` | common |
| ab-family | 家庭、成员、邀请码、`Scope` | `fam_` | user |
| ab-receipt / ab-analytics | 新增 `user_id` 归属与 scope 过滤 | `rcp_` `ana_` | family |

DAG：`common ← merchant/product/ocr/user ← family ← receipt ← analytics`。

## 关键设计
- **会话**：不透明令牌 `ab_`+32 字节，库存 SHA-256，30 天过期；`AuthInterceptor` 拦截 `/api/**`（放行 `/api/auth/**`、`/api/meta`）。
- **短信**：验证码存哈希，TTL 300s，重发间隔 60s，最多 5 次尝试；`SmsSender` 为扩展点，默认写日志。`dev-mode` 下 `sms/send` 返回 `debugCode`。
- **OAuth**：服务端授权码流程 + state（`usr_oauth_state`）；回调换取资料后建 `usr_auth_account`，再发一次性 ticket（`usr_login_ticket`）重定向到 `/#/login?ticket=…`，前端补上定位后 `POST /api/auth/ticket` 换会话。绑定模式带 `bind_user_id`，回到 `/#/profile?bound=…`。凭据缺失且 dev-mode 时走 mock。
- **定位**：每次登录（短信或 ticket）写 `usr_login_location`。
- **数据归属**：`rcp_receipt.user_id`、`ana_price_fact/daily.user_id`；0 为遗留数据，首个注册用户通过同步事件 `UserRegisteredEvent(first=true)` 在注册事务内认领。
- **Scope**：`scope=me|family`；`Scope.userIds` 经 `FamilyApi.memberUserIds` 展开；分析缓存键与 ETag 含排序后的 uid 列表；`ProjectionService.REVISION=2`，V2 迁移丢弃并重建投影表。
- **前端**：`login/profile/family` 三个页面；`ab.token` 存 localStorage；401 清会话跳登录；图片通过 fetch→blob 加载。

## 安全
- 令牌、验证码、ticket 均只存哈希；ticket 单次且短时有效；state 一次性。
- 固定验证码 `ab.auth.sms.fixed-code`（`AB_SMS_FIXED_CODE`，默认 `000000`）：任意手机号可凭它登录，不发短信；`/api/auth/providers` 仅在 dev-mode 下返回 `testCode`。对接短信网关时清空即可。
- 定位 `ab.auth.require-location` 默认 false：成功才写 `usr_login_location`，失败不阻断登录。
- 注销账号：`UserService.deleteAccount` 在单个事务内发布 `UserDeletedEvent`（family/receipt 同步监听，analytics 经 `ReceiptDeletedEvent` 更新投影），再删除 usr_* 数据。
- 对外部署必须 `AB_AUTH_DEV=false`，并设置 `AB_PUBLIC_BASE_URL` 与各提供商凭据。
- 浏览器定位需要 HTTPS（localhost 除外）。
