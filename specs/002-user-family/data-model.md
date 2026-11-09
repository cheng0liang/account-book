# 数据模型

权威定义见：
- `ab-user/src/main/resources/db/migration/user/V1__user.sql`
- `ab-family/src/main/resources/db/migration/family/V1__family.sql`
- `ab-receipt/.../receipt/V2__receipt_user.sql`、`ab-analytics/.../analytics/V2__analytics_user.sql`

## ab-user
- `usr_user`：id、nickname、phone（唯一，可空）、gender（UNKNOWN/MALE/FEMALE/OTHER）、address、birth_date、country、avatar_url、status、时间戳。
- `usr_login_location`（用户定位关联表）：user_id、provider、latitude、longitude、accuracy_m、**located_at**、ip、user_agent。登录时定位成功才写一行；账号注销时删除。
- `usr_auth_account`（账户授权登录关联表）：user_id、provider（APPLE/GOOGLE/WECHAT/QQ/LINE；手机号登录对应 usr_user.phone）、provider_uid、union_id、nickname、email、avatar_url、raw_profile；`UNIQUE(provider, provider_uid)`。
- `usr_session` / `usr_sms_code` / `usr_oauth_state` / `usr_login_ticket`：登录过程状态，均只存哈希。

- 注销账号（`UserService.deleteAccount`）：同一事务内先发布 `UserDeletedEvent`（family 退出/解散、receipt 删除小票并发布 `ReceiptDeletedEvent` 更新分析投影），再删除 usr_* 行（含该手机号的 `usr_sms_code`）。

## ab-family
- `fam_family`：id、name、owner_id、invite_code（唯一）、created_at。
- `fam_member`：family_id、user_id（**UNIQUE**，一人一家庭）、role（OWNER/MEMBER）、joined_at。

## 既有表变更
- `rcp_receipt.user_id`（0=遗留）；`ana_price_fact.user_id`、`ana_price_daily.user_id`（并入主键）。
- 无跨模块外键；`user_id` 仅按值引用。
