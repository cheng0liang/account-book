# API 契约（增量）

除 `/api/auth/**`、`/api/meta` 外均需 `Authorization: Bearer <token>`，缺失/过期 → 401 `auth.required`。

## 认证 `/api/auth`
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/providers` | `{sms, providers:[{id,mock}], requireLocation}` |
| POST | `/sms/send` | `{phone}`；dev-mode 返回 `debugCode` |
| POST | `/sms/login` | `{phone, code, location:{latitude,longitude,accuracy,locatedAt}}` → `{token, user, newUser}` |
| GET | `/oauth/{provider}/authorize` | 登录：302 到提供商；带 Bearer 时为绑定 |
| GET/POST | `/oauth/{provider}/callback` | 302 到 `/#/login?ticket=…` 或 `?error=state|denied|bound_other|failed`（POST 供 Apple form_post） |
| POST | `/ticket` | `{ticket, location}` → 同 sms/login |

provider ∈ apple/google/wechat/qq/line。

## 我 `/api/me`
GET 资料；PUT 更新（nickname/gender/birthDate/country/address）；GET `/accounts` 已绑定账号；GET `/locations` 最近登录定位；POST `/logout`；DELETE 注销账号（204；级联删除用户、会话、定位、第三方绑定，并通过 `UserDeletedEvent` 让 family 退出/解散家庭、receipt 删除其全部小票与图片；若是仍有其他成员的家庭创建者则 409 `family.ownerCannotDelete`）。

## 家庭 `/api/family`
GET 我的家庭（含成员；无则 204/null）；POST 创建 `{name}`；POST `/join` `{code}`；POST `/leave`；DELETE 解散（OWNER）；DELETE `/members/{userId}` 移出（OWNER）；POST `/invite-code/reset`（OWNER）。

## Scope 参数
`GET /api/receipts`、`/api/receipts/{id}` 及全部 `/api/analytics/**` 接受 `scope=me|family`（默认 me）。小票响应与列表项新增 `owner:{id,nickname,avatarUrl,me}`。
错误：`receipt.ownerOnly` 403（非本人改/删），`receipt.notFound` 404（不可见）。
