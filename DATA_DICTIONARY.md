# 数据字典

| 表 | 用途 | 关键约束 |
|---|---|---|
| `accounts` | 账户与子账户 | `id` 主键；允许负期初余额；显式账户类型与资产纳入开关 |
| `categories` | 收入/支出分类 | 方向、必要性、固定性、生活成本与应急金属性 |
| `transactions` | 逐笔及周期汇总交易 | 金额为正整数分；日期为 epoch day；指纹唯一；`recordGranularity` 区分天/周/月/季/年，汇总记录保存周期起止日期；转账有目标账户且分类必须为空 |
| `tags` | 标签 | 名称唯一 |
| `transaction_tags` | 交易—标签多对多 | 复合主键 |
| `merchants` | 规范化商户与收入来源 | `normalizedName` 唯一；记录显示名、常用分类、最近使用时间和使用次数，用于模糊候选 |
| `budgets` | 月/年/分类预算 | 时间范围、结转模式、阈值、循环开关；`NONE` 为固定预算，`NET` 为按上月差额动态增减 |
| `goals` | 收入、消费、结余、储蓄率、资产、应急金和自定义目标 | 比例用 basis points，60.00% 存 6000 |
| `balance_snapshots` | 账户历史余额快照 | `(accountId, snapshotYearMonth)` 唯一；现有历史精度为 `MONTH_ONLY` |
| `legacy_annual_summaries` | 历史年度真实汇总 | 周期起止月份唯一；与交易表完全分离 |
| `recurring_rules` | 重复交易提醒/待确认模板 | 默认 `reminderOnly=true`，不自动入账 |
| `import_batches` | CSV 导入批次 | 文件 SHA-256 唯一，记录成功/跳过/错误数和应用版本 |
| `attachments` | 私有附件元数据 | 文件本体不进数据库，仅保存相对路径 |

## 枚举

- 交易：`EXPENSE`、`INCOME`、`TRANSFER`、`REFUND`、`BALANCE_ADJUSTMENT`。
- 状态：`CONFIRMED`、`PENDING`、`DRAFT`、`DELETED`。
- 来源：`MANUAL`、`CSV`、`LEGACY`、`RECURRING`。
- 记录粒度：`DAY`、`WEEK`、`MONTH`、`QUARTER`、`YEAR`。
- 账户：`CASH`、`BANK`、`PAYMENT_PLATFORM`、`SAVINGS`、`INVESTMENT`、`CREDIT`、`LIABILITY`、`OTHER`。
- 周期：`MONTH`、`NATURAL_YEAR`、`FISCAL_YEAR`、`CUSTOM`。
