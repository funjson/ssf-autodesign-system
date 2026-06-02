# 06 UI Annotation Handoff

> 本文件用于把原型图层与产品规格连接起来，供前端、测试和评审使用。

## 0. 标注规则

| rule_id | 规则 |
|---|---|
| ANNO-RULE-001 | 每个页面 frame 使用 `SCR-xxx English Name` 命名 |
| ANNO-RULE-002 | 每个关键组件 layer 使用 `CMP-xxx English Name` 命名 |
| ANNO-RULE-003 | 组件 annotation 记录 FEAT / BR / AC，但不显示给普通用户 |
| ANNO-RULE-004 | 交互连线命名使用 PFLOW ID |
| ANNO-RULE-005 | 缺失或待确认处必须写成“待确认”，不得由原型工具自行补功能 |

## 1. 页面级标注

| panno_id | screen_id | frame_name | 页面目标 | 必须标注 |
|---|---|---|---|---|
| PANN-001 | SCR-001 | SCR-001 Interest Onboarding | 快速选择或跳过兴趣标签 | FEAT-002 / FEAT-006 / AC-011 / AC-012 |
| PANN-002 | SCR-002 | SCR-002 Daily Brief | 5 分钟个性化简报 | FEAT-001 / FEAT-003 / FEAT-004 / FEAT-007 / FEAT-010 |
| PANN-003 | SCR-003 | SCR-003 Interest Management | 管理标签、排序和排除 | FEAT-002 / BR-002 / AC-003 / AC-004 |
| PANN-004 | SCR-004 | SCR-004 Event Detail | 展示摘要、来源、多视角和原文 | FEAT-004 / FEAT-005 / FEAT-009 |
| PANN-005 | SCR-005 | SCR-005 Saved And History | 查看保存、稍后读、历史 | FEAT-009 / AC-017 / AC-018 |
| PANN-006 | SCR-006 | SCR-006 Settings And Privacy | 管理通知、个性化和隐私 | FEAT-008 / FEAT-010 / AC-015 / AC-020 |

## 2. 组件级标注

| panno_id | screen_id | component_id | layer_name | 关联功能 | 关联规则 | 关联验收 | 标注说明 |
|---|---|---|---|---|---|---|---|
| PANN-101 | SCR-001 | CMP-102 | CMP-102 Tag Grid | FEAT-002 / FEAT-006 | BR-002 / BR-017 | AC-011 | 标签 chip 支持多选，已选态必须明显 |
| PANN-102 | SCR-001 | CMP-105 | CMP-105 Done Button | FEAT-006 | BR-016 | AC-011 | 标签可为空或已选，点击进入简报 |
| PANN-103 | SCR-001 | CMP-106 | CMP-106 Skip Button | FEAT-006 | BR-016 | AC-012 | 跳过进入默认简报 |
| PANN-201 | SCR-002 | CMP-202 | CMP-202 Reading Time | FEAT-001 | BR-005 | AC-001 | 显示预计阅读时长和更新时间 |
| PANN-202 | SCR-002 | CMP-203 | CMP-203 News Card List | FEAT-001 / FEAT-004 / FEAT-005 | BR-005 / BR-004 / BR-007 / BR-011 | AC-001 / AC-007 / AC-009 | 新闻卡必须展示标题、摘要、来源、时间、标签 |
| PANN-203 | SCR-002 | CMP-205 | CMP-205 Feedback Menu | FEAT-003 / FEAT-007 | BR-006 / BR-018 / BR-019 | AC-005 / AC-013 / AC-014 | 菜单区分反馈和保存动作，执行后有 toast |
| PANN-204 | SCR-002 | CMP-207 | CMP-207 Why Recommended | FEAT-010 | BR-010 | AC-019 | 展示推荐原因，避免绝对化 |
| PANN-205 | SCR-002 | CMP-208 | CMP-208 Tab Bar | FEAT-001 / FEAT-009 / FEAT-010 | 无 | UIA-004 | 只含简报、保存、设置 |
| PANN-301 | SCR-003 | CMP-302 | CMP-302 Followed Tags | FEAT-002 | BR-002 | AC-003 | 支持排序和删除 |
| PANN-302 | SCR-003 | CMP-303 | CMP-303 Excluded Tags | FEAT-002 | BR-002 | AC-003 | 排除标签与关注标签视觉区分 |
| PANN-303 | SCR-003 | CMP-306 | CMP-306 Save Changes | FEAT-002 | BR-002 | AC-003 / AC-004 | 保存失败保留编辑状态 |
| PANN-401 | SCR-004 | CMP-402 | CMP-402 Summary Card | FEAT-005 | BR-007 | AC-009 | 摘要必须关联来源，不生成无来源摘要 |
| PANN-402 | SCR-004 | CMP-403 | CMP-403 Source List | FEAT-005 | BR-007 / BR-015 | AC-009 / AC-010 | 来源、时间、原文入口必须可见 |
| PANN-403 | SCR-004 | CMP-405 | CMP-405 Save Actions | FEAT-009 | BR-009 | AC-017 | 收藏/稍后读成功后状态变化 |
| PANN-501 | SCR-005 | CMP-501 | CMP-501 Segmented Control | FEAT-009 | BR-009 | AC-017 | 收藏、稍后读、历史可切换 |
| PANN-502 | SCR-005 | CMP-503 | CMP-503 Empty State | FEAT-009 | BR-021 | AC-018 | 空态提供回简报入口 |
| PANN-601 | SCR-006 | CMP-601 | CMP-601 Personalization Info | FEAT-010 | BR-010 | AC-019 | 个性化说明至少包含标签或反馈依据 |
| PANN-602 | SCR-006 | CMP-603 | CMP-603 Preference Controls | FEAT-010 | BR-010 / BR-022 | AC-020 | 清除偏好和关闭个性化必须二次确认 |
| PANN-603 | SCR-006 | CMP-604 / CMP-605 / CMP-606 | CMP-604-606 Notification Settings | FEAT-008 | BR-008 / BR-020 | AC-015 / AC-016 | 通知范围、频率上限、免打扰同组展示 |
| PANN-604 | SCR-006 | CMP-607 | CMP-607 Manage Interests | FEAT-002 | BR-002 | AC-003 | 点击进入 SCR-003 |

## 3. 流程标注

| pflow_id | 连接 | 标注说明 | 关联验收 |
|---|---|---|---|
| PFLOW-001 | SCR-001 CMP-105 -> SCR-002；SCR-002 -> SCR-003；SCR-003 CMP-306 -> SCR-002 | 新用户标签初始化和后续调整闭环 | AC-011 / AC-012 / AC-003 |
| PFLOW-002 | SCR-002 CMP-203 -> SCR-004；SCR-004 CMP-405 -> SCR-005；SCR-005 CMP-502 -> SCR-004 | 快读、来源透明和稍后读闭环 | AC-001 / AC-009 / AC-017 |
| PFLOW-003 | SCR-002 CMP-205/CMP-207 -> SCR-006 -> SCR-003 | 反馈训练、推荐解释、隐私控制和标签管理 | AC-005 / AC-019 / AC-020 / AC-015 |

## 4. 开发交付注意

| handoff_id | 注意事项 |
|---|---|
| HANDOFF-001 | 用户可见文案应使用产品文案，不显示 ID |
| HANDOFF-002 | 原型中所有新闻数据来自 mock sample data，开发不得当作真实数据源 |
| HANDOFF-003 | 如果原型工具自动添加额外页面，应在评审中移除 |
| HANDOFF-004 | 若真实原型未生成，本文件只表示输入包可用，不代表视觉稿已验收 |

## 5. 标注缺口

无。当前输入包已覆盖 SCR-001 至 SCR-006 的关键页面、组件和流程标注。

