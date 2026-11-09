# Quickstart

## 环境
- JDK 17、Maven 3.9+。无需安装 Nacos / 数据库。
- 若本机 Maven 访问仓库需要系统证书：`export MAVEN_OPTS="-Djavax.net.ssl.trustStoreType=KeychainStore -Djavax.net.ssl.trustStore=NONE"`。

## 构建与测试
```bash
mvn -q verify                       # 全部单元/集成/ArchUnit 测试
node ab-app/src/test/frontend/i18n-check.mjs   # 三语言 key 一致性（Node ≥ 18，无依赖）
mvn -pl ab-app -am clean package    # 产出 ab-app/target/account-book.jar（务必 clean，否则可能留下旧的 repackage jar）
```

## 运行（无 Nacos）
```bash
java -jar ab-app/target/account-book.jar
# 浏览器打开 http://localhost:8080 ；手机请用同网段 IP 访问（浏览器仅在 HTTPS 或 localhost 下允许调用相机，其它情况请用"选择图片"）
curl localhost:8080/actuator/health   # {"status":"UP"}
```
数据写入 `./data/`（SQLite 文件 + 图片）。可用 `AB_DATA_DIR` 修改数据目录、`PORT` 修改端口（默认 8080）。

## OCR 配置
| 目标 | 配置 |
|---|---|
| 无 key 演示 | 默认 `ab.ocr.provider=auto` → `mock` |
| 真实识别 | `AB_OCR_API_KEY=sk-... AB_OCR_BASE_URL=https://.../v1 AB_OCR_MODEL=gpt-4o-mini`（auto 自动切到 llm） |
| 通义（默认） | `AB_OCR_API_KEY=<百炼 key>`；模型用 `AB_OCR_MODEL` 切换，key 所属工作空间需已开通该模型 |
| 关闭识别 | `--ab.ocr.provider=noop`（得到空表单，手工录入） |

### OCR 排错
| 日志 | 原因 |
|---|---|
| `PKIX path building failed` | JVM 不信任本机代理根证书；启动加 `-Djavax.net.ssl.trustStoreType=KeychainStore -Djavax.net.ssl.trustStore=NONE` |
| 404 `Model not exist` | 模型名不对，或该工作空间/地域没有此模型；`GET <base-url>/models` 可列出 key 可见的模型 |
| 403 `Model.AccessDenied` | key 有效但未开通该模型调用权限，到百炼控制台开通 |
| 401 | key 无效或已轮换 |

## 使用 Nacos（可选）
```bash
export NACOS_ADDR=127.0.0.1:8848   # 另有 NACOS_NAMESPACE / NACOS_GROUP / NACOS_USERNAME / NACOS_PASSWORD / NACOS_ENABLED
# Data ID: account-book.yml，Group: DEFAULT_GROUP（NACOS_GROUP 可改），内容为任意 application.yml 覆盖项
```
Nacos 不可达时应用仍正常启动并使用 `application.yml`。

## 手动验证清单
1. 打开首页 → "扫描" → 选一张图片 → 看到可编辑草稿与"模拟识别结果"警告 → 保存。
2. "记录"页可见新小票；点开可编辑/删除。
3. "分析"页：趋势 / 对比 / 消费三个标签有图；再次刷新（DevTools 网络面板）图表请求返回 304。
4. 设置页切换三种语言，界面无遗漏的 key 原文。
5. 浏览器控制台无报错。

## 重建投影
`curl -X POST localhost:8080/api/analytics/rebuild`（升级投影逻辑或怀疑数据不一致时使用）。

## 代码索引（CodeGraph）
`codegraph init`（已建立索引则跳过）后，用 `codegraph explore "<符号或问题>"` 定位代码；大改后 `codegraph sync`。
