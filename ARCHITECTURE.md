# 架构说明

## 分层与数据流

项目当前采用单 `app` 模块，以包边界保持后续多模块拆分能力：

```text
Compose Screen
    ↓ UiAction / immutable state
Hilt ViewModel + StateFlow
    ↓
Domain engines / use-case style repository methods
    ↓
Offline Repository
    ↓
Room DAO / DataStore / WorkManager / private files
```

- `core/model`：金额、周期、目标、仪表盘和聚合模型。
- `core/database`：Room 实体、DAO、数据库、migration 和历史种子。
- `data`：离线仓储、设置、CSV、备份、附件、报告和提醒。
- `domain`：预测与确定性 `InsightEngine`。
- `feature`：不可变 UI 状态、动作入口和 ViewModel。
- `ui`：Material 3 页面、主题和 Canvas 图表；不直接访问 DAO。

## 关键口径

- 金额始终为正数 `amountMinor: Long`；人民币以分为最小单位。余额调整通过 `balanceDirection` 表达方向。
- 有效支出为已确认支出减已确认退款，排除转账、调整、草稿、删除项和 `excludeFromBudget` 项。
- 结余为有效收入减有效支出；收入小于等于零时储蓄率为 `null`。
- 一笔转账同时保存转出与转入账户，仅改变两个账户余额，不计入收入或消费。
- 历史年度汇总和余额快照使用独立表；首次历史导入事务末尾断言交易数仍为零。
- 日期以 `LocalDate` 计算后存为 epoch day；不使用字符串比较日期。
- 周期汇总补录仍保存为单笔真实金额，使用 `recordGranularity` 和周期起止日期标明统计口径；不会伪造分摊到每天的交易。每日趋势只呈现 `DAY` 记录。支出汇总只停止支出日均外推，收入汇总按完整收入计入但不会干扰支出预测。
- 固定预算直接使用基础预算；动态预算按“基础预算 +（基础预算 − 上月实际支出）”计算。只有上月存在真实支出/退款记录时才结转，避免漏记月份被误判为节省整月预算。
- 转账和余额调整的 `categoryId` 始终为空，并使用独立业务图标；商户名称在本地规范化后累计使用次数，Compose 只通过防抖后的 Repository Flow 获取模糊候选。
- Room 聚合查询返回 `Flow`，写入后首页、流水、目标和分析自动刷新。

## 备份恢复

创建备份前执行 WAL checkpoint。ZIP 生成到应用缓存，再流式写入 SAF 目标；可选密码将整个 ZIP 用 PBKDF2-HMAC-SHA256 派生的 AES-256-GCM 密钥加密。恢复过程先验证路径、manifest 和数据库 SHA-256，再保存当前 SQLite 安全副本、暂存替换并恢复附件/设置；替换后发生异常会从安全副本回滚。成功后重启进程以重新创建 Room/DataStore 单例。

## 自适应与无障碍

宽度小于 840dp 使用底部导航，大屏使用 `NavigationRail`。点击目标至少由 Material 组件提供 48dp 触控区域；颜色状态同时带图标和文字；Canvas 图表提供 `contentDescription`；所有关键金额共享全局隐藏设置。
