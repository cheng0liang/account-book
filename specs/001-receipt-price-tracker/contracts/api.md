# REST API 契约 v1

基础约定
- 前缀 `/api`，JSON UTF-8；金额在 API 中为**十进制数（主单位）**，例如 `1280` / `12.5`；币种为 ISO-4217（`JPY` `CNY` `TWD` `USD` ...）。
- 语言：请求头 `Accept-Language: zh-CN | ja | en`，影响错误消息文案。
- 日期：`yyyy-MM-dd`；日期时间（商户本地时间，无时区）：`yyyy-MM-dd'T'HH:mm`。
- 错误：HTTP 4xx/5xx，体为 `{"code":"VALIDATION_FAILED","message":"<本地化文案>","details":[{"field":"items[0].name","message":"..."}]}`。
  常见 code：`VALIDATION_FAILED`(400) `NOT_FOUND`(404) `CONFLICT`(409) `OCR_FAILED`(502) `PAYLOAD_TOO_LARGE`(413) `INTERNAL_ERROR`(500)。
- 分页：`page`(从 0 起)、`size`(默认 20，最大 100)，响应 `{"items":[...],"total":123}`。

## 枚举
| 名称 | 取值 |
|---|---|
| merchantType | `SUPERMARKET` `CONVENIENCE` `RESTAURANT` `CAFE` `DRUGSTORE` `DEPARTMENT` `ELECTRONICS` `CLOTHING` `ONLINE` `GAS` `OTHER` |
| productCategory | `FOOD` `BEVERAGE` `FRESH` `DAILY` `HOUSEHOLD` `MEDICINE` `BEAUTY` `CLOTHING` `ELECTRONICS` `OTHER` |
| paymentMethod | `CASH` `CREDIT_CARD` `DEBIT_CARD` `E_MONEY` `QR_PAY` `OTHER` |
| specUnit | `g` `kg` `ml` `l` `pcs` |
| source | `OCR` `MANUAL` |

## 0. 元数据
`GET /api/meta` →
```json
{ "version":"0.1.0", "defaultCurrency":"JPY", "ocrProvider":"mock",
  "currencies":["JPY","CNY","TWD","USD","EUR"],
  "merchantTypes":["SUPERMARKET",...], "productCategories":["FOOD",...],
  "paymentMethods":["CASH",...], "specUnits":["g","kg","ml","l","pcs"] }
```

## 1. 商户（ab-merchant）
Merchant：
```json
{ "id":1, "name":"西友 涩谷店", "country":"JP", "city":"东京", "district":"涩谷",
  "type":"SUPERMARKET", "address":"...", "latitude":35.659, "longitude":139.700,
  "createdAt":1730868000000 }
```
`country` 为 ISO-3166 alpha-2（`JP` `CN` `TW` `US` ...），`city`/`district`(商圈) 为自由文本。除 `name` 外全部可空。

- `GET /api/merchants?q=&country=&city=&district=&type=&page=&size=` → `{items:[Merchant],total}`（`q` 模糊匹配名称）
- `GET /api/merchants/{id}` → Merchant
- `POST /api/merchants` body=Merchant(无 id) → 201 Merchant（同名+同城市已存在则 409 `CONFLICT`）
- `PUT /api/merchants/{id}` → Merchant

## 2. 商品（ab-product）
Product：
```json
{ "id":1, "name":"明治牛奶", "brand":"明治", "category":"BEVERAGE",
  "specText":"1000ml", "specValue":1000, "specUnit":"ml", "specDim":"VOLUME", "specBaseQty":1000,
  "barcode":"4902705...", "tags":["牛奶","冷藏"], "createdAt":1730868000000 }
```
`specDim`(`WEIGHT`/`VOLUME`/`COUNT`) 与 `specBaseQty`（折算到 g / ml / 件的数量）由服务端根据 `specValue`+`specUnit`（或解析 `specText`）派生，只读；分析接口的"标准单位价"（每100g / 每100ml / 每件）基于它们计算。
- `GET /api/products?q=&brand=&category=&tag=&page=&size=` → `{items:[Product],total}`
- `GET /api/products/{id}` / `POST /api/products` / `PUT /api/products/{id}`（`tags` 为整体覆盖）
- `GET /api/products/brands?q=` → `["明治","森永"]`；`GET /api/products/tags?q=` → `["牛奶","冷藏"]`（用于输入联想）

## 3. 小票（ab-receipt + ab-ocr）
### 3.1 识别（拍照/选图）
`POST /api/receipts/recognize`，`multipart/form-data`，字段 `image`（jpg/png/webp/heic，≤10MB，客户端应先压缩到长边≤1600px）→
```json
{ "imageToken":"2026/11/3f9c...jpg", "provider":"mock",
  "warnings":["ocr_mock_data","merchant_not_matched","total_mismatch"],
  "draft": <ReceiptForm，见下，不含 id；merchant.id / items[].product.id 在能匹配到已有记录时已填充> ,
  "merchantCandidates":[Merchant], "productCandidates":{"0":[Product],"2":[Product]} }
```
`warnings` 为机器可读代码，前端以 `warning.<code>` 为 key 走 i18n：`ocr_mock_data`（模拟识别结果）、`merchant_not_matched`（未匹配到已有商户）、`total_mismatch`（识别合计与明细之和不一致）。
`productCandidates` 的 key 为 `draft.items` 的下标；`provider=noop` 时 draft 为空表单（仅含默认币种与当前时间），前端照常展示可编辑表单。

### 3.2 ReceiptForm（创建/修改请求体，也是详情响应体）
```json
{ "id":10,
  "merchant":{ "id":null, "name":"西友 涩谷店", "country":"JP", "city":"东京", "district":"涩谷",
               "type":"SUPERMARKET", "address":null, "latitude":null, "longitude":null },
  "purchasedAt":"2026-11-06T12:30", "currency":"JPY",
  "totalAmount":1280, "taxAmount":94, "discountAmount":0,
  "paymentMethod":"CASH", "note":"", "source":"OCR",
  "imageToken":"2026/11/3f9c...jpg",
  "imageUrl":"/api/receipts/10/image",
  "items":[
    { "id":null,
      "product":{ "id":null, "name":"明治牛奶", "brand":"明治", "category":"BEVERAGE",
                  "specText":"1000ml","specValue":1000,"specUnit":"ml","barcode":null,"tags":["牛奶"] },
      "quantity":2, "unitPrice":198, "discount":0, "amount":396, "note":null } ] }
```
规则：`merchant.id` 有值 → 直接关联（忽略其余字段）；无值 → 按"名称+城市"查找，找不到则创建。`product` 同理（按"名称+品牌+规格"）。`amount` 缺省时服务端计算为 `quantity*unitPrice-discount`。`totalAmount` 缺省时取各行 amount 之和。`imageToken` 仅创建时传，读取时返回 `imageUrl`（无图则为 null）。必填：`merchant.name`、`purchasedAt`、`currency`、`items`≥1 且每行 `product.name`、`quantity>0`、`unitPrice>=0`。

### 3.3 CRUD
- `POST /api/receipts` body=ReceiptForm → 201 ReceiptForm
- `PUT /api/receipts/{id}` body=ReceiptForm → ReceiptForm（整体覆盖明细）
- `GET /api/receipts/{id}` → ReceiptForm；`DELETE /api/receipts/{id}` → 204
- `GET /api/receipts/{id}/image` → 图片二进制
- `GET /api/receipts?from=&to=&merchantId=&q=&page=&size=` → `{items:[ReceiptSummary],total}`，按 `purchasedAt` 倒序
```json
{ "id":10, "merchantId":3, "merchantName":"西友 涩谷店", "purchasedAt":"2026-11-06T12:30",
  "currency":"JPY", "totalAmount":1280, "itemCount":5, "paymentMethod":"CASH", "hasImage":true,
  "firstItems":["明治牛奶","面包"] }
```

## 4. 分析（ab-analytics，读预聚合表，带 ETag）
通用过滤参数（均可选）：`from` `to`(yyyy-MM-dd) `currency`(缺省=meta.defaultCurrency；不同币种不混算) `country` `city` `district` `merchantType` `merchantId` `productId` `brand` `category` `tag`。
通用图表响应：
```json
{ "currency":"JPY", "unit":"item|100g|100ml", "labels":["2026-10","2026-11"],
  "series":[ {"key":"m:3","name":"西友 涩谷店","data":[198, null]} ],
  "counts":[3,5], "version":42 }
```
`data` 与 `labels` 等长，缺失为 `null`。响应头 `ETag: "v42-<hash>"` + `Cache-Control: no-cache, private`，请求带 `If-None-Match` 命中返回 304。`version` 为 `ana_meta.version`，任何影响投影的写入（小票增删改、商户/商品变更）都会使其递增。
可选附加字段（`NON_NULL`，仅在适用时出现）：`meta`（与 labels 对齐的 `{id,name}`）、`total`、`count`。周粒度（`granularity=week`）的 label 为该周周一的 `yyyy-MM-dd`；`from`/`to` 两端均为闭区间。
`POST /api/analytics/rebuild` → 204：清空并从小票事实全量重建投影（运维/升级用；启动时若 `ana_meta.projection_rev` 与代码内 `REVISION` 不一致也会自动重建）。

- `GET /api/analytics/facets` → `{currencies:[],countries:[],cities:[{country,city}],districts:[{country,city,district}],merchantTypes:[],brands:[],categories:[],tags:[],dateRange:{min,max}}`
- `GET /api/analytics/price-trend?productId=必填&merchantIds=1,2,3&granularity=day|week|month&metric=avg|min|max&normalized=false` — 同一商品在各商户的价格走势；`merchantIds` 缺省 = 该商品购买次数最多的前 6 家商户。`normalized=true` 时按标准单位价（每100g/每100ml/每件）。
- `GET /api/analytics/price-compare?dimension=merchant|city|district|country|merchantType|brand|product&normalized=false&...过滤` — 某维度下的价格对比（柱状图）；`series` 固定三条：`avg` `min` `max`，`counts` 为样本数；`dimension=merchant` 需 `productId`（或 tag/brand/category）；`dimension=product` 需 `merchantId`（或 brand/tag/category）。附加 `meta:[{"id":3,"name":"..."}]`（与 labels 对齐，便于点击跳转）。
- `GET /api/analytics/spending?groupBy=day|month|merchant|merchantType|category|brand|city|district&topN=10` — 消费统计，`series` 单条 `amount`，附 `total`（各行金额之和）、`count`（去重小票数）；`counts` 为各分组的明细行数。首页月度汇总使用 `groupBy=day&from=月初&to=月末`。
- `GET /api/analytics/price-table?productId=必填&...过滤` → `{currency, rows:[{merchantId,merchantName,city,district,latestPrice,latestDate,minPrice,avgPrice,maxPrice,count}]}`，按 `latestPrice` 升序（"哪里买最便宜"列表）。
