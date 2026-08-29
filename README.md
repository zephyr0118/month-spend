# 月迹（YueJi）

月迹是一款以“按月掌握消费、结余和资产增长”为中心的 Android 原生个人财务应用。应用不需要账号，不申请网络权限；交易、账户、历史快照、年度汇总、附件和设置默认全部保存在应用私有目录。

## 已实现能力

- 首次启动可导入规格文档中的 2023—2026 真实余额快照和 FY2024—FY2026 年度汇总，或创建空白账本；导入事务会校验全部合计，且不会生成虚假的月度交易。
- 账户与子账户、可负余额、是否计入总资产、账户类型和期初余额管理。
- 支出、收入、单行原子转账、关联字段退款、正/负余额调整；金额使用 `Long` 分，不使用 `Double`。
- 支出和收入支持按天、周、月、季度或年度汇总补录；日期决定归属周期，流水会明确标记汇总粒度，适合漏记后的周/月/季/年补账及按月工资收入。
- 快速记账、复制上一笔、图片附件、标签、每月重复提醒模板、保存撤销、编辑、软删除和删除撤销。
- 暖黄色品牌视觉与低阴影卡片：首页提供 8 个高频分类直达入口，记账面板提供内置数字键盘和图标分类网格，常用路径无需多层跳转。
- 面向每日使用的快捷路径：保存按钮固定在记账面板底部，支持今天/昨天/日历日期选择、数字键盘触觉反馈、最近账户与同类型分类自动沿用，以及首页最近三笔账单直接复查和编辑。
- 可在“我的 → 默认付款账户”指定新账单账户；账户管理支持上移/下移排序。记账面板打开即完整展开，日期与商户/来源始终可见，流水图标使用分类保存的真实图标。
- 商户与收入来源会保存在本地商户库；再次输入任意关键字时可模糊匹配历史名称、查看使用次数并直接选择，常用分类也可随候选项恢复。
- 月度首页：消费、收入、退款后有效支出、结余、储蓄率、预算剩余、状态文字、月底预测、分类前五、每日趋势、目标、总资产、账户分布和规则化提示。流水标题优先显示商户/收入来源，分类在次要信息行展示；没有来源时才以分类作为主标题。
- 月末预测同时展示预计收入、预计支出和预计结余；收入汇总不会停止支出的日均预测。月预算可选择固定模式，或按上月实际支出差额进行动态增减。
- 流水时间轴、日历视图、搜索、类型筛选、账户/分类/标签/必要性/固定性/一次性/可报销/来源/金额高级筛选及批量删除。
- 概览、消费、收入、结余、资产、账户、行为和数据质量分析；支持分类、账户、商户、标签、必要/可选、固定/可变、一次性/经常性维度。
- 月预算和 11 类目标；目标可修改类型、名称、金额/比例和截止日期，删除前需要确认；支持自然年及任意起始月的自定义财务年。
- UTF-8/GB18030 CSV 导入、字段校验、指纹去重、逐行错误报告和标准 CSV 导出。
- `.yueji` ZIP 完整备份（清单、SQLite、JSON 设置、附件、SHA-256 校验），可选 PBKDF2/AES-256-GCM 密码加密；恢复前创建安全副本，失败自动回滚。
- PDF 月报与年度历史回顾。
- 深色模式、动态取色、金额隐藏、系统字体缩放、图表语义、手机底部导航和平板导航栏。
- 窗口使用 `preferredRefreshRate` 请求设备最高刷新率；Android 15 及以上还会让实际 Compose 视图树投票最高帧率、启用触摸升频并关闭窗口级刷新率省电平衡。一加/OxygenOS 等提供每应用刷新率控制的系统，可从“我的 → 高刷新率”直达显示设置并为月迹选择 120Hz。
- WorkManager 每日/月中/月末提醒；设备凭据/生物识别应用锁；最近任务/截图保护。
- Room schema v4 与显式 1→2、2→3、3→4 migration；JVM、Room 集成、迁移和 Compose UI 测试。

## 技术栈

Kotlin、Jetpack Compose、Material 3、单 Activity、Room、DataStore、WorkManager、Hilt、Coroutines、StateFlow、Navigation 风格的单向状态流、Kotlin Serialization 运行库、JUnit、AndroidX Test 和 Compose UI Test。依赖统一锁定于 `gradle/libs.versions.toml`。应用 Manifest 中没有 `INTERNET` 权限。

构建环境基于：

- Android Gradle Plugin 8.13.0
- Gradle 8.14.3
- Java 17 工具链（Gradle 本身可由兼容的更高 JDK 启动）
- `compileSdk` Android 36.1，`targetSdk` 36，`minSdk` 26

## 运行

1. 用支持 AGP 8.13 的 Android Studio 打开仓库根目录。
2. 确认 SDK Manager 已安装 Android SDK Platform 36.1 和 Build Tools 36.x。
3. 选择 `app` 配置及 API 26 或更高的设备并运行。

开发调试构建：

```powershell
.\gradlew.bat :app:assembleDebug
```

供手机日常使用的优化构建：

```powershell
.\gradlew.bat :app:assembleDaily
```

可直接安装的日用 APK 位于：

```text
app/build/outputs/apk/daily/app-daily.apk
```

安装：

```powershell
adb install -r app/build/outputs/apk/daily/app-daily.apk
```

1.4.2 日用 APK 沿用旧版的包名 `com.yueji.finance.debug` 与同一调试签名，并将 `versionCode` 提升到 7，因此使用上面的 `-r` 参数会覆盖安装 1.0.0—1.4.1 并保留原应用的 Room 数据库与 DataStore 设置。该变体关闭调试运行时并启用 R8 优化和资源压缩，日常性能测试请使用它，而不是 `app-debug.apk`。请勿先卸载旧版；卸载会按 Android 的常规行为移除应用私有数据。

正式发布前请配置自己的签名密钥；`assembleRelease` 默认生成未签名的压缩 Release APK，不应直接分发。

## 测试与质量检查

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:compileDebugAndroidTestKotlin
.\gradlew.bat :app:connectedDebugAndroidTest
.\gradlew.bat :app:lintDebug
```

- JVM 测试覆盖金额精度、收支/储蓄率、无收入、财务年、自然年、闰年/月末、月底预测、资产目标、应急金、洞察优先级和全部历史合计。
- 仪器测试覆盖 Room 收支聚合、退款、转账账户效果、余额调整、唯一索引、10,000 条交易聚合、1→2 migration 和首次启动界面。
- `connectedDebugAndroidTest` 需要已连接设备或已安装系统镜像的模拟器。

## CSV 格式

标准首行：

```csv
id,type,amount,currency,date,time,account,destination_account,category,subcategory,merchant,tags,note,necessity,variability,is_one_off,is_reimbursable,exclude_from_budget,record_granularity,period_start,period_end
```

`amount` 使用十进制元，导入后转换为整数分。`tags` 使用 `|` 或 `;` 分隔。没有原始 ID 时，按日期、时间、金额、类型、账户、商户和备注生成 SHA-256 指纹。重复文件批次和重复交易默认跳过。

`record_granularity` 可为 `DAY`、`WEEK`、`MONTH`、`QUARTER`、`YEAR`；旧 CSV 缺少这些字段时默认按天导入。`period_start` 与 `period_end` 可省略，应用会根据日期和记录粒度自动推导。

## 修改历史种子

历史账户、快照、年度汇总和预置分类集中在：

```text
app/src/main/java/com/yueji/finance/core/database/SeedData.kt
```

修改后必须同步更新 `SeedDataTest`，并确保：

- 每个月份的账户快照合计等于规定总余额；
- `收入 - 消费 = 结余`；
- 历史导入完成后 `transactions` 表仍为空。

## 数据与安全说明

- 数据库：应用私有目录中的 `yueji.db`，启用 WAL；所有 UI 通过 Repository/Flow 访问，不在 Compose 重组中查询数据库。
- 附件：`files/attachments/`；数据库仅保存相对路径和元数据。
- 日志和诊断页面不输出金额、余额、商户或备注。
- `.yueji` 是敏感文件。即使启用密码，也应在受信任位置保管。
- 清空数据需要输入“清空月迹”，随后返回首次启动流程。

更多内容见 [ARCHITECTURE.md](ARCHITECTURE.md)、[DATA_DICTIONARY.md](DATA_DICTIONARY.md) 和 [CHANGELOG.md](CHANGELOG.md)。
