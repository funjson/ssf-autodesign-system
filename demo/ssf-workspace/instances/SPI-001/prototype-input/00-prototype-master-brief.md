# 00 Prototype Master Brief

> 本文件是给原型生成工具的总输入包说明，来源于当前实例的产品规格。它不是新的产品事实源；如需修改产品定义，必须回到 `product-spec/` 执行 change-run。

## 0. 元信息

| 字段 | 内容 |
|---|---|
| document_id | PROTO-MASTER-BRIEF-001 |
| instance_id | SPI-001 |
| product_name | 每日新闻 App |
| version | v0.1 |
| generated_at | 2026-05-27 |
| source_documents | PRD-001 / FEATURE-SPEC-001 / UI-IA-001 / UI-SPEC-001 / PROTOTYPE-ANNOTATION-001 / BASELINE-CHANGE-001 |
| output_scope | Figma Make / Motiff / Uizard / 前端原型生成输入 |
| review_gate | prototype-input-auto-review |
| review_status | ready_for_review |

## 1. 产品压缩背景

每日新闻 App 面向希望在短时间内获取自己感兴趣内容的用户。产品用“个性化标签 + 约 5 分钟今日简报 + 事件聚合去重 + 来源透明 + 反馈训练 + 克制通知”降低信息爆炸带来的认知负担。

原型重点不是做新闻门户、热榜或社区，而是验证用户能否快速完成三件事：

| 目标 ID | 目标 | 关联规格 |
|---|---|---|
| PGOAL-001 | 快速看到与自己相关的今日新闻 | FEAT-001 / BR-005 / AC-001 |
| PGOAL-002 | 通过标签定制和反馈修正信息流 | FEAT-002 / FEAT-003 / FEAT-007 / BR-002 / BR-006 |
| PGOAL-003 | 从快读进入可信深读，看到来源和多视角 | FEAT-004 / FEAT-005 / BR-004 / BR-007 |
| PGOAL-004 | 控制通知、个性化和隐私边界 | FEAT-008 / FEAT-010 / BR-008 / BR-010 |

## 2. 原型范围

| scope_id | 必须生成 | 说明 |
|---|---|---|
| PSCOPE-001 | 6 个移动端页面 | 仅生成 SCR-001 至 SCR-006，不新增未定义页面 |
| PSCOPE-002 | 3 条关键可点击流程 | 新用户进入简报、快读到深读、反馈与隐私控制 |
| PSCOPE-003 | 关键组件标注 | 保留 SCR / CMP / FEAT / BR / AC ID，用于层命名和交付说明 |
| PSCOPE-004 | 样例新闻数据 | 全部为 mock 数据，不声称真实新闻事实 |
| PSCOPE-005 | 状态表达 | 默认、加载、空态、错误、权限提示按页面需要覆盖 |

| scope_id | 不生成 | 原因 |
|---|---|---|
| POUT-001 | 社区评论、发帖、关注作者 | 已在 DEP-001 废弃 |
| POUT-002 | 广告、订阅、商业化入口 | 干扰快读目标 |
| POUT-003 | 完整采编后台或原创新闻生产 | 不属于当前产品定位 |
| POUT-004 | 事实裁判、媒体评分系统 | 当前只做来源透明和多视角 |
| POUT-005 | 未定义的账号、支付、企业权限 | 不在当前 v0.1 范围 |

## 3. 页面总表

| screen_id | 页面名称 | 页面目标 | 核心组件 | 关联功能 |
|---|---|---|---|---|
| SCR-001 | 兴趣初始化页 | 新用户快速选择或跳过兴趣标签 | CMP-101 至 CMP-106 | FEAT-002 / FEAT-006 |
| SCR-002 | 今日简报页 | 展示约 5 分钟个性化新闻简报 | CMP-201 至 CMP-208 | FEAT-001 / FEAT-003 / FEAT-004 / FEAT-007 / FEAT-010 |
| SCR-003 | 标签管理页 | 管理兴趣标签、排序和排除 | CMP-301 至 CMP-306 | FEAT-002 / FEAT-006 / FEAT-010 |
| SCR-004 | 事件详情页 | 展示摘要、来源、多视角、原文和操作 | CMP-401 至 CMP-406 | FEAT-004 / FEAT-005 / FEAT-007 / FEAT-009 |
| SCR-005 | 保存与历史页 | 查看收藏、稍后读和阅读历史 | CMP-501 至 CMP-504 / CMP-208 | FEAT-009 / FEAT-007 |
| SCR-006 | 设置与隐私页 | 管理通知、个性化说明和隐私控制 | CMP-601 至 CMP-607 | FEAT-003 / FEAT-008 / FEAT-010 / FEAT-002 |

## 4. 关键流程

| flow_id | 流程名称 | 页面顺序 | 验证重点 |
|---|---|---|---|
| PFLOW-001 | 新用户进入个性化简报 | SCR-001 -> SCR-002 -> SCR-003 -> SCR-002 | 标签选择、跳过、调整后回到简报 |
| PFLOW-002 | 从快读进入可信深读 | SCR-002 -> SCR-004 -> SCR-005 -> SCR-004 | 新闻卡、来源、多视角、稍后读 |
| PFLOW-003 | 个性化反馈与隐私控制 | SCR-002 -> SCR-006 -> SCR-003 | 反馈菜单、为什么推荐、清除偏好、通知设置 |

## 5. ID 保留策略

| ID 类型 | 保留位置 | 不允许出现的位置 |
|---|---|---|
| SCR-xxx | frame 名称、页面注释、交付文档 | 用户可见标题正文 |
| CMP-xxx | layer 名称、组件注释、Figma section 名称 | 按钮文案、卡片正文 |
| FEAT-xxx / BR-xxx / AC-xxx | annotation、handoff 表、开发备注 | 用户界面文案 |
| PFLOW-xxx / PANN-xxx | 原型流程注释、交付说明 | 用户界面文案 |

## 6. 总生成指令

```text
请基于本 prototype-input 包生成一个高保真可点击移动端原型。
产品：每日新闻 App。
目标：让用户用约 5 分钟看到与自己兴趣相关的重要新闻，并能通过标签、反馈、来源和隐私设置控制信息流。
只生成 SCR-001 至 SCR-006 六个页面，并连接 PFLOW-001 至 PFLOW-003 三条流程。
页面、组件和标注必须保留 SCR/CMP/FEAT/BR/AC/PFLOW/PANN ID，但这些 ID 只能用于 frame、layer、annotation、handoff，不得作为用户可见 UI 文案。
所有新闻、来源、时间和标签均使用 sample data 中的 mock 数据，不得暗示为真实新闻。
视觉风格保持清晰、可信、低噪音，避免营销 hero、热榜、评论流、广告、复杂装饰和未定义功能。
```

