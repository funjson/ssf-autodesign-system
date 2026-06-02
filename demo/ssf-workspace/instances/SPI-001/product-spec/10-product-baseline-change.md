# 10 产品基线与变更说明

> 本文档解决需求变更时 AI 不知道旧系统是什么的问题。它记录当前产品基线、关键决策、已确认规格和每次变更的影响范围。

## 0. 文档元信息

| 字段 | 内容 |
|---|---|
| document_id | BASELINE-CHANGE-001 |
| instance_id | SPI-001 |
| current_version | v0.1 |
| previous_version | 无 |
| related_documents | REQ-ANALYSIS-001 / PRD-001 / PRODUCT-ARCH-001 / FEATURE-SPEC-001 / UI-IA-001 / UI-SPEC-001 / PROTOTYPE-ANNOTATION-001 |
| generated_at | 2026-05-27 |
| review_gate | baseline-auto-review |
| review_status | ready_for_review |

## 1. 当前产品基线摘要

| 项目 | 当前基线 |
|---|---|
| 产品定位 | 面向碎片时间用户的个性化每日新闻快读 App，让用户用约 5 分钟读懂与自己相关的重要新闻。 |
| 目标用户 | 快速知情用户、兴趣驱动用户、专业追踪用户、轻量新闻用户。 |
| 核心目标 | 降低信息过载，提供标签可控、事件去重、来源透明、克制通知和可深读的新闻体验。 |
| 核心能力 | 个性化今日简报、兴趣标签、反馈训练、事件聚合、来源多视角、通知设置、阅读延展、个性化透明度。 |
| 产品模块 | MOD-001 今日简报；MOD-002 标签与偏好；MOD-003 内容反馈；MOD-004 事件聚合；MOD-005 来源与可信度；MOD-006 通知与提醒；MOD-007 阅读延展；MOD-008 隐私与透明度。 |
| 核心页面 | SCR-001 兴趣初始化；SCR-002 今日简报；SCR-003 标签管理；SCR-004 事件详情；SCR-005 保存与历史；SCR-006 设置与隐私。 |
| 核心流程 | 新用户进入简报；从快读进入深读；反馈训练与隐私控制。 |
| 关键业务规则 | BR-002 标签管理；BR-004 事件聚合；BR-005 快读简报；BR-006 反馈训练；BR-007 来源透明；BR-008 克制通知；BR-009 阅读延展；BR-010 透明度说明。 |

## 2. 版本历史

| version | change_id | change_type | 摘要 | 状态 |
|---|---|---|---|---|
| v0.1 | CHG-001 | 新增 | 初始产品基线：完成 Analysis、产品架构、PRD、功能任务、UI 规格、原型标注 | ready_for_review |

## 3. 产品架构基线

| module_id | 模块名称 | 当前状态 | 关联需求 | 关联功能任务 |
|---|---|---|---|---|
| MOD-001 | 今日简报模块 | approved | REQ-001 | FEAT-001 |
| MOD-002 | 标签与偏好模块 | approved | REQ-002 / REQ-006 | FEAT-002 / FEAT-006 |
| MOD-003 | 内容反馈模块 | approved | REQ-003 / REQ-007 | FEAT-003 / FEAT-007 |
| MOD-004 | 事件聚合模块 | approved | REQ-004 | FEAT-004 |
| MOD-005 | 来源与可信度模块 | approved | REQ-005 | FEAT-005 |
| MOD-006 | 通知与提醒模块 | approved | REQ-008 | FEAT-008 |
| MOD-007 | 阅读延展模块 | approved | REQ-009 | FEAT-009 |
| MOD-008 | 隐私与透明度模块 | approved | REQ-003 / REQ-010 | FEAT-010 |

## 4. 功能基线

| feature_id | 功能名称 | 当前状态 | 关联页面 | 关键规则 | 关键验收 |
|---|---|---|---|---|---|
| FEAT-001 | 个性化今日简报 | ready_for_review | SCR-002 / SCR-004 | BR-005 / BR-011 | AC-001 / AC-002 |
| FEAT-002 | 兴趣标签选择与编辑 | ready_for_review | SCR-001 / SCR-003 | BR-002 / BR-012 | AC-003 / AC-004 |
| FEAT-003 | 内容反馈与偏好训练 | ready_for_review | SCR-002 / SCR-006 | BR-006 / BR-013 | AC-005 / AC-006 |
| FEAT-004 | 同事件聚合与去重 | ready_for_review | SCR-002 / SCR-004 | BR-004 / BR-014 | AC-007 / AC-008 |
| FEAT-005 | 摘要来源与多视角入口 | ready_for_review | SCR-004 | BR-007 / BR-015 | AC-009 / AC-010 |
| FEAT-006 | 轻量化兴趣初始化 | ready_for_review | SCR-001 / SCR-002 | BR-016 / BR-017 | AC-011 / AC-012 |
| FEAT-007 | 新闻卡反馈菜单 | ready_for_review | SCR-002 / SCR-004 / SCR-005 | BR-018 / BR-019 | AC-013 / AC-014 |
| FEAT-008 | 克制型标签通知 | ready_for_review | SCR-006 / SCR-004 | BR-008 / BR-020 | AC-015 / AC-016 |
| FEAT-009 | 收藏、稍后读与历史 | ready_for_review | SCR-005 / SCR-004 | BR-009 / BR-021 | AC-017 / AC-018 |
| FEAT-010 | 个性化说明与偏好清除 | ready_for_review | SCR-006 / SCR-002 | BR-010 / BR-022 | AC-019 / AC-020 |

## 5. 页面基线

| screen_id | 页面名称 | 页面职责 | 关联功能 | 当前状态 |
|---|---|---|---|---|
| SCR-001 | 兴趣初始化页 | 新用户轻量选择或跳过标签 | FEAT-002 / FEAT-006 | ready_for_review |
| SCR-002 | 今日简报页 | 展示个性化快读简报和新闻卡操作 | FEAT-001 / FEAT-003 / FEAT-004 / FEAT-007 / FEAT-010 | ready_for_review |
| SCR-003 | 标签管理页 | 管理兴趣标签、排序和排除 | FEAT-002 / FEAT-006 / FEAT-010 | ready_for_review |
| SCR-004 | 事件详情页 | 展示摘要、来源、多视角和原文入口 | FEAT-004 / FEAT-005 / FEAT-007 / FEAT-009 | ready_for_review |
| SCR-005 | 保存与历史页 | 查看收藏、稍后读和历史 | FEAT-009 / FEAT-007 | ready_for_review |
| SCR-006 | 设置与隐私页 | 管理通知、个性化说明和偏好清除 | FEAT-003 / FEAT-008 / FEAT-010 / FEAT-002 | ready_for_review |

## 6. 业务规则基线

| rule_id | 规则名称 | 规则内容 | 关联功能 | 当前状态 |
|---|---|---|---|---|
| BR-002 | 标签管理规则 | 标签需支持关注、排除、未选择状态 | FEAT-002 | ready_for_review |
| BR-004 | 事件聚合规则 | 同一事件多篇报道合并为事件卡并保留来源列表 | FEAT-004 | ready_for_review |
| BR-005 | 快读简报规则 | 默认展示 6-10 条高相关新闻卡和预计阅读时长 | FEAT-001 | ready_for_review |
| BR-006 | 反馈训练规则 | 反馈必须绑定对象、类型和时间，并可撤销最近操作 | FEAT-003 | ready_for_review |
| BR-007 | 来源透明规则 | 摘要或详情必须显示来源、时间和原文入口 | FEAT-005 | ready_for_review |
| BR-008 | 克制通知规则 | 通知必须符合标签范围、重要度、频率上限和免打扰 | FEAT-008 | ready_for_review |
| BR-009 | 阅读延展规则 | 收藏、稍后读、历史需区分状态并支持移除保存项 | FEAT-009 | ready_for_review |
| BR-010 | 透明度说明规则 | 提供推荐原因、清除偏好和个性化开关，破坏性操作二次确认 | FEAT-010 | ready_for_review |
| BR-011 | 新闻卡展示规则 | 新闻卡必须显示标题、摘要、来源、时间、关联标签和主操作 | FEAT-001 | ready_for_review |
| BR-018 | 新闻卡菜单规则 | 操作菜单必须区分反馈类与保存类动作 | FEAT-007 | ready_for_review |

## 7. 关键决策日志

| decision_id | 决策问题 | 最终决策 | 选择原因 | 影响范围 | 关联文档 |
|---|---|---|---|---|---|
| DEC-001 | 是否采用 8 模块产品架构 | 接受 MOD-001 至 MOD-008 | 用户回复“接受”，模块边界清楚且覆盖核心需求 | MOD / FEAT / SCR | PRODUCT-ARCH-001 / APR-002 |
| DEC-002 | 是否把标签作为一级模块 | 接受，保留 MOD-002 | 定制化标签是用户明确要求的必须能力 | FEAT-002 / FEAT-006 / SCR-001 / SCR-003 | PRODUCT-ARCH-001 |
| DEC-003 | 是否独立事件聚合模块 | 接受，保留 MOD-004 | 去重是解决信息爆炸的核心支撑 | FEAT-004 / SCR-004 | PRODUCT-ARCH-001 |
| DEC-004 | 来源与可信度是否做事实裁判 | 不做事实裁判，只做透明展示、多视角和摘要回溯 | 避免越界和过度承诺 | FEAT-005 / SCR-004 | PRD-001 / FEATURE-SPEC-001 |
| DEC-005 | 通知模块是否保留 | 保留，但定位为克制型提醒 | 通知不能制造新的信息过载 | FEAT-008 / SCR-006 | PRODUCT-ARCH-001 |

## 8. 废弃设计清单

| deprecated_id | 废弃内容 | 原 ID | 废弃原因 | 替代方案 |
|---|---|---|---|---|
| DEP-001 | 社区评论、发帖、关注作者 | DROP-001 | 与快速获取内容目标不一致 | 新闻卡反馈和来源透明 |
| DEP-002 | 新闻采编后台和原创新闻生产 | DROP-002 | 当前定位不是媒体生产系统 | 内容聚合与来源展示 |
| DEP-003 | 完整商业化、广告和订阅方案 | DROP-003 | 用户未要求且会干扰核心体验 | 保留为后续商业决策 |
| DEP-004 | 重度长视频新闻体验 | DROP-004 | 与短时间获取内容目标冲突 | 摘要、事件详情和原文入口 |

---

## 9. 变更说明

# CHG-001 初始产品基线

## 9.1 变更元信息

| 字段 | 内容 |
|---|---|
| change_id | CHG-001 |
| base_version | 无 |
| target_version | v0.1 |
| change_type | 新增 |
| change_reason | 用户提出每日新闻 App 系统设计，需要完整产品设计事实源 |
| requester | 用户 |

## 9.2 变更前

- 原功能：无
- 原模块：无
- 原页面：无
- 原规则：无
- 原验收：无
- 原行为描述：当前工作区无旧产品基线。

## 9.3 变更后

- 新功能行为：建立 FEAT-001 至 FEAT-010。
- 新产品模块：建立 MOD-001 至 MOD-008。
- 新页面表现：建立 SCR-001 至 SCR-006。
- 新业务规则：建立 BR-002 / BR-004 至 BR-011 / BR-018 等核心规则。
- 新验收标准：建立 AC-001 至 AC-020。

## 9.4 影响范围分析

| affected_type | affected_id | 影响说明 | 是否改变边界 | 需要修改的内容 |
|---|---|---|---|---|
| PRD | PRD-001 | 新增产品上下文、目标、范围和角色 | 不适用 | 新建 |
| PRODUCT_ARCH | PRODUCT-ARCH-001 | 新增首版产品架构 | 不适用 | 新建 |
| MODULE | MOD-001 至 MOD-008 | 新增首版模块 | 不适用 | 新建 |
| FEATURE | FEAT-001 至 FEAT-010 | 新增首版功能任务 | 不适用 | 新建 |
| SCREEN | SCR-001 至 SCR-006 | 新增首版页面 | 不适用 | 新建 |
| COMPONENT | CMP-101 至 CMP-607 | 新增首版关键组件 | 不适用 | 新建 |
| AC | AC-001 至 AC-020 | 新增首版功能验收 | 不适用 | 新建 |
| TEST | UIA-001 至 UIA-017 | 新增 UI 断言 | 不适用 | 新建 |

## 9.5 回归关注点

| regression_id | 回归对象 | 回归原因 | 关联验收 |
|---|---|---|---|
| REG-001 | FEAT-001 / SCR-002 | 今日简报是主入口，后续变更需确保快读体验不退化 | AC-001 / AC-002 |
| REG-002 | FEAT-002 / SCR-003 | 标签是核心必须能力，后续变更需确保可编辑和可保存 | AC-003 / AC-004 |
| REG-003 | FEAT-005 / SCR-004 | 来源透明是信任底座，后续变更不能隐藏来源入口 | AC-009 / AC-010 |
| REG-004 | FEAT-008 / SCR-006 | 通知不能变成高频增长推送 | AC-015 / AC-016 |
| REG-005 | FEAT-010 / SCR-006 | 个性化控制和清除偏好不能被移除 | AC-019 / AC-020 |

## 9.6 待确认问题

| question_id | 问题 | 影响范围 | 建议处理 |
|---|---|---|---|
| Q-BASE-001 | 首发地区、语言、内容来源和账号同步仍待确认 | 内容源 / UI 文案 / 合规 / 同步 | 作为后续 change-run 输入 |

## 10. Baseline Auto Review

| check_id | 检查项 | 结果 | 问题 | 修复动作 |
|---|---|---|---|---|
| CHECK-BASE-001 | 是否有 current_version、previous_version 和版本历史 | pass | 已记录 v0.1 和 CHG-001 | 无 |
| CHECK-BASE-002 | 是否有模块、功能、页面、规则基线 | pass | 第 3 至 6 节已记录 | 无 |
| CHECK-BASE-003 | 是否有关键决策和废弃设计 | pass | 第 7 至 8 节已记录 | 无 |
| CHECK-BASE-004 | 是否有变更前后、影响范围和回归关注点 | pass | CHG-001 已记录 | 无 |
