# 实例 Manifest

> 本文件记录单个产品设计实例的身份、状态、文档树和最近变更。Agent 进入实例后必须先读取本文件。

## 1. 实例身份

| 字段 | 内容 |
|---|---|
| instance_id | SPI-001 |
| instance_name | 每日新闻 App 初始设计 |
| product_name | 每日新闻 App |
| task_type | 新系统 |
| workflow_type | full-run |
| current_phase | completed |
| active_gate | none |
| blocked | no |
| current_blocker | 无 |
| next_allowed_actions | prototype-run / change-run / doc-run / discuss-only |
| status | completed |
| created_at | 2026-05-26 |
| updated_at | 2026-05-27 |
| root_path | ssf-workspace/instances/SPI-001 |

## 2. 实例摘要

| 项目 | 内容 |
|---|---|
| 产品形态 | App |
| 业务领域 | 新闻聚合 / 个性化内容消费 / 信息效率工具 |
| 目标用户 | 希望在短时间内获取自己感兴趣内容信息的用户 |
| 核心目标 | 降低信息过载，通过个性化标签、摘要、筛选和深读入口帮助用户快速获取高相关内容 |
| 当前焦点 | v0.1 产品设计包已完成，prototype-input 原型输入包已生成，可进入真实原型生成或后续变更 |
| 已有材料 | 用户当前描述；公开竞品资料；模型假设 |
| 关键约束 | 内容版权、来源可信度、个性化隐私、推荐偏见、通知打扰控制、AI 摘要准确性 |

## 3. 文档状态

| 文档 | 路径 | 状态 | 最近更新时间 | review_gate / 说明 |
|---|---|---|---|---|
| Intake 信息收集 | intake.md | completed | 2026-05-26 | 公共入口能力，不计入产品版本 |
| 01 分析输入与信息收集 | product-spec/01-analysis-input.md | approved | 2026-05-27 | analysis-human-review / APR-001 |
| 02 产品调研与洞察报告 | product-spec/02-research-insight.md | approved | 2026-05-27 | analysis-human-review / APR-001 |
| 03 需求分析说明 | product-spec/03-requirement-analysis.md | approved | 2026-05-27 | analysis-human-review / APR-001 |
| 04 产品架构设计 | product-spec/04-product-architecture.md | approved | 2026-05-27 | product-architecture-human-review / APR-002 |
| 05 PRD 产品需求文档 | product-spec/05-prd.md | ready_for_review | 2026-05-27 | prd-auto-review |
| 06 功能任务规格文档 | product-spec/06-feature-task-spec.md | ready_for_review | 2026-05-27 | feature-spec-auto-review |
| 07 UI 信息架构与页面清单 | product-spec/07-ui-ia-screen-inventory.md | ready_for_review | 2026-05-27 | ui-ia-auto-review |
| 08 结构化 UI/交互规格 | product-spec/08-structured-ui-interaction-spec.md | ready_for_review | 2026-05-27 | ui-spec-auto-review |
| 09 原型生成 Prompt 与 UI 标注说明 | product-spec/09-prototype-prompt-ui-annotation.md | ready_for_review | 2026-05-27 | prototype-auto-review |
| 10 产品基线与变更说明 | product-spec/10-product-baseline-change.md | ready_for_review | 2026-05-27 | baseline-auto-review |
| Prototype Input 原型输入包 | prototype-input/ | ready_for_review | 2026-05-27 | prototype-input-auto-review |

## 4. Review Gate 状态

| gate_id | gate_type | status | must_stop | blocked | evidence_id | updated_at | blocking_issues |
|---|---|---|---|---|---|---|---|
| analysis-human-review | human_review | approved | yes | no | APR-001 | 2026-05-27 | 无 |
| product-architecture-human-review | human_review | approved | yes | no | APR-002 | 2026-05-27 | 无 |
| product-architecture-delta-review | auto_review | pending | no | no | 无 |  | 尚未进入变更 |
| prd-auto-review | auto_review | pass | no | no | CHECK-PRD-001 / CHECK-PRD-002 / CHECK-PRD-003 / CHECK-PRD-004 | 2026-05-27 | 无 |
| feature-spec-auto-review | auto_review | pass | no | no | CHECK-FEAT-001 / CHECK-FEAT-002 / CHECK-FEAT-003 / CHECK-FEAT-004 | 2026-05-27 | 无 |
| ui-ia-auto-review | auto_review | pass | no | no | CHECK-UIIA-001 / CHECK-UIIA-002 / CHECK-UIIA-003 | 2026-05-27 | 无 |
| ui-spec-auto-review | auto_review | pass | no | no | CHECK-UISPEC-001 / CHECK-UISPEC-002 / CHECK-UISPEC-003 / CHECK-UISPEC-004 | 2026-05-27 | 无 |
| prototype-auto-review | auto_review | pass | no | no | CHECK-PROT-001 / CHECK-PROT-002 / CHECK-PROT-003 / CHECK-PROT-004 / CHECK-PROT-005 | 2026-05-27 | 无 |
| baseline-auto-review | auto_review | pass | no | no | CHECK-BASE-001 / CHECK-BASE-002 / CHECK-BASE-003 / CHECK-BASE-004 | 2026-05-27 | 无 |
| prototype-input-auto-review | auto_review | pass | no | no | CHECK-PINPUT-001 / CHECK-PINPUT-002 / CHECK-PINPUT-003 / CHECK-PINPUT-004 / CHECK-PINPUT-005 / CHECK-PINPUT-006 | 2026-05-27 | CHECK-PROTOTYPE-001 至 CHECK-PROTOTYPE-006 保持 pending，因尚未生成真实原型 |
| change-run-local-review | auto_review | pending | no | no | 无 |  | 尚未进入变更 |

## 5. 人工确认记录

> 只有本节存在对应 `APR-xxx` 时，人工 Review Gate 才能写 `approved`。不得由模型自行推断人工确认。

| approval_id | gate_id | user_words | approved_scope | approval_result | approved_at | notes |
|---|---|---|---|---|---|---|
| APR-001 | analysis-human-review | 你继续吧 | 01-analysis-input.md / 02-research-insight.md / 03-requirement-analysis.md | approved | 2026-05-27 | 用户允许继续进入 Design 阶段 |
| APR-002 | product-architecture-human-review | 接受 | 04-product-architecture.md | approved | 2026-05-27 | 用户确认产品架构，可继续后续 Design 文档 |

## 6. 自动检查记录

| check_run_id | gate_id | checked_documents | result | failed_checks | repair_action |
|---|---|---|---|---|---|
| ACR-001 | analysis-human-review | product-spec/01-analysis-input.md / product-spec/02-research-insight.md / product-spec/03-requirement-analysis.md | pass | 无 | 人工评审已通过，证据 APR-001 |
| ACR-002 | prd-auto-review | product-spec/05-prd.md | pass | 无 | 无 |
| ACR-003 | feature-spec-auto-review | product-spec/06-feature-task-spec.md | pass | 无 | 无 |
| ACR-004 | ui-ia-auto-review | product-spec/07-ui-ia-screen-inventory.md | pass | 无 | 无 |
| ACR-005 | ui-spec-auto-review | product-spec/08-structured-ui-interaction-spec.md | pass | 无 | 无 |
| ACR-006 | prototype-auto-review | product-spec/09-prototype-prompt-ui-annotation.md | pass | 无 | 无 |
| ACR-007 | baseline-auto-review | product-spec/10-product-baseline-change.md | pass | 无 | 无 |
| ACR-008 | prototype-input-auto-review | prototype-input/00-prototype-master-brief.md / prototype-input/01-design-system-constraints.md / prototype-input/02-screen-contracts.md / prototype-input/03-flow-contracts.md / prototype-input/04-sample-data.md / prototype-input/05-figma-make-prompts.md / prototype-input/06-ui-annotation-handoff.md / prototype-input/07-prototype-review-checklist.md | pass | 无 | 无 |

## 7. 产品架构 Delta 记录

| architecture_delta_id | change_id | changed_items | boundary_changed | review_gate | result | notes |
|---|---|---|---|---|---|---|
| ARCH-DELTA-001 | 无 | 首次生成产品架构，不属于局部变更 | 不适用 | product-architecture-human-review | approved | 初版产品架构已由用户确认，证据 APR-002 |

## 8. 运行记录

| time | execution_mode | user_intent | result | affected_documents |
|---|---|---|---|---|
| 2026-05-26 | full-run | 为每日新闻 App 做系统设计，解决信息爆炸，支持定制化标签 | 完成 Analysis 阶段草案，阻塞于 analysis-human-review | intake.md / product-spec/01-analysis-input.md / product-spec/02-research-insight.md / product-spec/03-requirement-analysis.md |
| 2026-05-27 | full-run | 用户回复“你继续吧”，继续产品设计 | 记录 APR-001，生成产品架构草案，阻塞于 product-architecture-human-review | manifest.md / product-spec/04-product-architecture.md |
| 2026-05-27 | full-run | 用户回复“接受”，确认产品架构 | 记录 APR-002，生成 PRD 与功能任务规格 | manifest.md / product-spec/04-product-architecture.md / product-spec/05-prd.md / product-spec/06-feature-task-spec.md |
| 2026-05-27 | full-run | 完成后续 Design 文档 | 生成 UI 信息架构、结构化 UI、原型标注和产品基线；自动检查通过 | product-spec/07-ui-ia-screen-inventory.md / product-spec/08-structured-ui-interaction-spec.md / product-spec/09-prototype-prompt-ui-annotation.md / product-spec/10-product-baseline-change.md |
| 2026-05-27 | prototype-run | 基于当前实例输出 prototype-input | 生成 prototype-input/00-07；输入包自检通过；真实原型检查保持 pending | prototype-input/ |

## 9. 下一步建议动作

| action_id | 建议动作 | 触发条件 | 是否允许自动执行 |
|---|---|---|---|
| NEXT-001 | 基于 v0.1 执行 change-run | 用户提出产品范围、模块、功能、页面或规则变更 | 需先判断影响范围 |
| NEXT-002 | 执行 repair-run | 任一 auto_review fail | 是 |
| NEXT-003 | 执行 doc-run | 用户要求单独修改某份文档 | 是 |
| NEXT-004 | 基于 prototype-input 生成真实原型 | 用户要求生成 Figma / Motiff / Uizard / 前端可点击原型 | 是 |

## 10. 待确认问题

| question_id | 问题 | 影响范围 | 建议处理 |
|---|---|---|---|
| Q-001 | 首发地区和语言是中文、英文，还是多语言？ | 内容源、标签体系、UI、合规 | Analysis 评审时确认 |
| Q-002 | 是否允许使用 AI 摘要和 AI 标签归类？ | 需求、产品架构、可信度规则、验收 | Analysis 评审时确认 |
| Q-003 | 内容来源是开放聚合、合作媒体、RSS，还是用户自定义来源？ | 产品架构、商业模式、版权风险 | Analysis 评审时确认 |
| Q-004 | 是否需要账号体系和跨设备同步？ | 产品架构、隐私、功能任务 | Design 前确认 |
