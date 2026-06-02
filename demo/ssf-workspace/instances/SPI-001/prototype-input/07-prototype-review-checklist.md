# 07 Prototype Review Checklist

> 本文件区分“prototype-input 输入包检查”和“真实原型检查”。当前只生成输入包，尚未生成真实 Figma / Motiff / Uizard 原型。

## 0. 元信息

| 字段 | 内容 |
|---|---|
| document_id | PROTO-REVIEW-CHECKLIST-001 |
| instance_id | SPI-001 |
| generated_at | 2026-05-27 |
| review_gate | prototype-input-auto-review |
| review_status | pass |

## 1. Prototype Input Auto Review

| check_id | 检查项 | 通过标准 | 结果 | 问题 | 修复动作 |
|---|---|---|---|---|---|
| CHECK-PINPUT-001 | 输入包文件完整性 | 00 至 07 共 8 个文件齐全 | pass | 无 | 无 |
| CHECK-PINPUT-002 | 来源文档可追踪 | 输入包引用 PRD / Feature / UI IA / UI Spec / Prototype Annotation / Baseline | pass | 无 | 无 |
| CHECK-PINPUT-003 | 页面范围一致 | 只定义 SCR-001 至 SCR-006 | pass | 无 | 无 |
| CHECK-PINPUT-004 | ID 保留策略正确 | SCR/CMP/FEAT/BR/AC 用于 frame/layer/annotation，不作为用户可见文案 | pass | 无 | 无 |
| CHECK-PINPUT-005 | 流程契约完整 | PFLOW-001 至 PFLOW-003 均有步骤和异常路径 | pass | 无 | 无 |
| CHECK-PINPUT-006 | 样例数据安全 | 新闻和来源均标注为 mock 数据，不声称真实新闻 | pass | 无 | 无 |

## 2. Real Prototype Review

| check_id | 检查项 | 通过标准 | 当前状态 | 说明 |
|---|---|---|---|---|
| CHECK-PROTOTYPE-001 | 是否已生成真实原型 | 存在 Figma / Motiff / Uizard / 前端原型链接或文件 | pending | 尚未生成真实原型 |
| CHECK-PROTOTYPE-002 | 页面是否全部落图 | SCR-001 至 SCR-006 均有画板 | pending | 尚未生成真实原型 |
| CHECK-PROTOTYPE-003 | 交互是否可点击 | PFLOW-001 至 PFLOW-003 可点击走通 | pending | 尚未生成真实原型 |
| CHECK-PROTOTYPE-004 | 视觉是否符合设计约束 | 符合 01-design-system-constraints.md | pending | 尚未生成真实原型 |
| CHECK-PROTOTYPE-005 | 标注是否在真实图层中保留 | frame/layer/annotation 含 ID，用户界面不显示 ID | pending | 尚未生成真实原型 |
| CHECK-PROTOTYPE-006 | 是否存在未定义功能 | 不出现热榜、评论、广告、账号、支付等未定义内容 | pending | 尚未生成真实原型 |

## 3. 评审问题

| question_id | 问题 | 影响范围 | 建议 |
|---|---|---|---|
| Q-PINPUT-001 | 是否需要同时输出深色模式原型？ | 全部页面 | 当前不生成；如需要，应作为后续变更 |
| Q-PINPUT-002 | 原文入口使用外部浏览器还是内嵌 WebView？ | SCR-004 / CMP-403 | 当前只保留入口，待版权和技术方案确认 |
| Q-PINPUT-003 | 首发地区、语言和内容来源是否需要在原型中显式表达？ | sample data / UI 文案 / 合规 | 当前使用中文 mock 数据，后续可 change-run |

## 4. 当前结论

prototype-input 输入包自检通过，可交给原型工具生成真实原型。真实原型尚未生成，因此 `CHECK-PROTOTYPE-001` 至 `CHECK-PROTOTYPE-006` 保持 pending。

