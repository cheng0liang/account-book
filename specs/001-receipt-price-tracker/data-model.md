# 数据模型

> 单一 SQLite 文件；每个模块独占表前缀；**跨模块无外键、无 JOIN**。权威定义见各模块 `db/migration/<module>/V*.sql`。

约定：`*_minor` = 最小货币单位整数（JPY 无小数；CNY/USD 两位小数）；`created_at/updated_at` = UTC epoch 毫秒；`purchased_at` = 商户本地时间 `yyyy-MM-dd'T'HH:mm`，`purchased_date` = `yyyy-MM-dd`。

## 1. ab-merchant
**mer_merchant**
| 列 | 说明 |
|---|---|
| id PK | |
| name / normalized_name | 展示名 / 归一化名（`NameNormalizer`：全半角、大小写、空白） |
| country | ISO-3166 alpha-2 |
| city / city_key | 城市 / 归一化城市（空为 `''`，用于唯一索引） |
| district | 商圈 |
| type | `SUPERMARKET` 等枚举，默认 `OTHER` |
| address, latitude, longitude | 可空；经纬度 REAL |

索引：`UNIQUE(normalized_name, city_key)`；`(country, city, district)`；`(type)`。

## 2. ab-product
**prd_product**：`name/normalized_name`、`brand/brand_key`、`category`、`spec_text`、`spec_key`、`spec_value`、`spec_unit`、`spec_dim`(WEIGHT/VOLUME/COUNT)、`spec_base_qty`（折算 g/ml/件）、`barcode`。
- 唯一：`(normalized_name, brand_key, spec_key)`。索引：brand、category、barcode。

**prd_product_tag**（WITHOUT ROWID）：`(product_id, tag)` PK，反向索引 `(tag, product_id)`。FK 仅限本模块（级联删除）。

## 3. ab-receipt
**rcp_receipt**：`merchant_id`、`merchant_name`（快照，列表无需访问 merchant）、`purchased_at`、`purchased_date`、`currency`、`total_minor`、`tax_minor`(可空)、`discount_minor`、`payment_method`、`note`、`source`(OCR/MANUAL)、`image_path`、`item_count`。
索引：`(purchased_at)`、`(purchased_date)`、`(merchant_id, purchased_at)`。

**rcp_receipt_item**：`receipt_id` FK(级联)、`line_no`、`product_id`、`product_name`(快照)、`quantity` REAL、`unit_price_minor`、`discount_minor`、`amount_minor`、`note`。
索引：`(receipt_id, line_no)`、`(product_id)`。

## 4. ab-analytics（读模型，可随时重建）
| 表 | 用途 | 键/索引 |
|---|---|---|
| ana_meta | `version`（数据版本，驱动 ETag/缓存失效）、`projection_rev`（投影结构版本） | k PK |
| ana_merchant_dim | 商户维度快照（name/country/city/district/type） | id PK |
| ana_product_dim | 商品维度快照（name/brand/category/spec_dim/spec_base_qty） | id PK |
| ana_product_tag | 商品标签（冗余） | PK(product_id, tag)；idx(tag, product_id) |
| ana_price_fact | 明细事实：一行一个小票行；`unit_minor = 净额/数量`（净额=行金额，已含折扣） | idx(receipt_id)；idx(purchased_date, merchant_id, product_id) |
| ana_price_daily | **日汇总**：`n, qty, amount_minor, min_unit, max_unit, sum_unit` | PK(product_id, merchant_id, currency, purchased_date)；idx(currency, purchased_date)；idx(merchant_id, purchased_date) |

读路径规则：所有图表查询 → `ana_price_daily` + 维表；`ana_price_fact` 仅用于桶重算与"消费统计"中需要去重小票数的场景。

## 5. 领域事件（同步发布，载荷可 JSON 序列化）
| 事件 | 发布者 | 内容 | 订阅者 |
|---|---|---|---|
| MerchantChangedEvent | merchant | 商户快照 | analytics |
| ProductChangedEvent | product | 商品快照 + tags | analytics |
| ReceiptSavedEvent | receipt | 小票头 + 行快照（含币种、日期、商户/商品 ID、数量、净额） | analytics |
| ReceiptDeletedEvent | receipt | receiptId | analytics |

## 6. 聚合口径
- **均价**：`sum_unit / n`（按小票行等权）；**min/max**：桶内 `min_unit/max_unit`。
- **标准单位价**：`unit / spec_base_qty × 100`（g/ml）或 `unit × 1`（件）。
- **消费合计**：行金额之和；**count**：去重小票数；`counts[]`：各分组的明细行数。
- **周粒度**：以周一日期作 label；`from/to` 闭区间。
