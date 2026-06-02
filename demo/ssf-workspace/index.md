# ssf-workspace 实例索引

> 本文件登记当前工作区中所有由 `ssf-product-pm` 管理的产品设计实例。Agent 每次生成或修改文件前必须先读取本索引，并基于本索引判断目标实例。

## 1. 索引元信息

| 字段 | 内容 |
|---|---|
| workspace_id | SSW-001 |
| created_at | 2026-05-26 |
| updated_at | 2026-05-27 |
| index_version | v0.4 |

## 2. 实例列表

> 实例列表必须使用下列表头，不得降级为旧版“当前状态/摘要/路径”短表。Agent 判断当前实例能否继续、是否阻塞、是否需要 repair-run，都必须优先读取本表。
>
> `index_version` 是本模板结构版本，不是每次生成的流水号。除非本模板升级，否则不得自行改成 `v0.8`、`v0.9` 等任意版本。

| instance_id | 实例名称 | 产品 / 项目名称 | 任务类型 | 当前阶段 | 当前状态 | 阻塞 Review Gate | 最近运行模式 | 下一步建议动作 | 最近更新时间 | 摘要 | 路径 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| SPI-001 | 每日新闻 App 初始设计 | 每日新闻 App | 新系统 | completed | completed | 无 | prototype-run | prototype-run / change-run / doc-run / discuss-only | 2026-05-27 | 已完成每日新闻 App v0.1 产品设计包，并基于当前规格生成 prototype-input 原型输入包。 | ssf-workspace/instances/SPI-001 |

禁止使用旧版短表：

```markdown
| instance_id | 产品名称 | 产品形态 | 业务领域 | 当前状态 | 最近更新时间 | 摘要 | 实例路径 |
```

## 3. 判断规则

- 若用户明确说“新建、另一个产品、不要覆盖之前的、重新开始”，创建新实例。
- 若用户明确说“继续、基于之前、修改现有、补齐上次”，优先匹配已有实例。
- 若用户明确说“重新生成、覆盖、再来一版、跳过某阶段、基于变更修改”，先执行 intake gate。
- 若用户输入的产品名称、业务领域、目标用户和核心目标与最近实例不一致，不得覆盖最近实例。
- 若无法判断是否同一实例，先列出候选实例并请用户确认。
- 若实例阻塞在人工评审节点，不得默认继续后续阶段。
- 若实例阻塞在自动评审失败节点，应优先 repair-run，不应重跑全量流程。
- 若用户只是局部变更，先读取目标实例 `manifest.md` 和 `10-product-baseline-change.md` 判断影响范围。
- 如果局部变更修改产品架构但不改变模块边界，阻塞 Review Gate 写 `无`，最近运行模式写 `change-run`，并在摘要中说明 `ARCH-DELTA-xxx`。

## 4. 最近活动

| time | action | instance_id | 说明 |
|---|---|---|---|
| 2026-05-26 | full-run analysis stage created | SPI-001 | 新建每日新闻 App 产品设计实例，完成 Analysis 阶段三份文档并停在人工评审。 |
| 2026-05-27 | product architecture generated | SPI-001 | 用户回复“你继续吧”，记录 APR-001，进入 Design 并生成产品架构，停在产品架构人工评审。 |
| 2026-05-27 | product architecture approved | SPI-001 | 用户回复“接受”，记录 APR-002，继续生成后续 Design 文档。 |
| 2026-05-27 | full product design completed | SPI-001 | 完成 PRD、功能任务、UI 信息架构、结构化 UI、原型标注和产品基线，自动检查通过。 |
| 2026-05-27 | prototype input generated | SPI-001 | 基于 v0.1 产品规格派生 prototype-input 原型输入包，未修改 product-spec。 |
