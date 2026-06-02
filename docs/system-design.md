# SSF Autodesign 系统设计 v0.1

## 1. 目标

SSF Autodesign 用来打通“需求原型”和“产品设计文档”。用户在系统里添加本地 `ssf-workspace`，选择 SPI 实例后，可以在中间区域预览原型结构，点击 SCR/CMP 需求块，在右侧看到与该块相关的产品设计事实，然后生成一次性 `change-request` 交给 Codex / Cursor / Claude CLI 做文档反向修改。

## 2. 第一版边界

第一版读取两类目录：

- `product-spec/`：产品设计事实源，重点读取 `06-feature-task-spec.md` 中的 FEAT / BR / AC。
- `prototype-input/`：原型生成输入与标注入口，重点读取 `02-screen-contracts.md` 和 `06-ui-annotation-handoff.md`。

第一版不直接调用 Figma，也不维护长期 AI 会话。系统生成 `change-requests/CR-xxx.md`，由用户或后续执行器交给 CLI 运行。

## 3. 核心流程

1. 用户创建系统项目。
2. 用户给项目添加一个本地 `ssf-workspace` 路径。
3. 后端扫描 `instances/SPI-*`，读取每个实例的 `manifest.md`。
4. 用户点击 SPI 实例。
5. 后端构建 SpecGraph：SCR、CMP、PANN 来自 `prototype-input`，FEAT、BR、AC 来自 `product-spec`。
6. 前端把 SpecGraph 渲染成可点击原型块。
7. 用户点击某个组件，在右侧查看关联上下文并输入修改意图。
8. 后端生成 `instances/SPI-xxx/change-requests/CR-xxx.md`。

## 4. 原型来源策略

中间画布不是默认假定一定有 Figma 产物。系统按以下优先级判断：

1. 如果 `requirement-prototype/frames/SCR-xxx.png|jpg|jpeg|webp|svg` 存在，展示用户提供的真实原型帧，并在页面上标识为“用户原型”。
2. 如果未检测到真实原型帧，使用 `prototype-input/02-screen-contracts.md` 自动合成结构化需求块，并标识为“系统合成预览”。
3. 在真实原型存在但尚未接入 layer-map 时，真实图片作为视觉主视图，`CMP` 列表作为临时可点击需求块。后续接入 layer-map 后，点击热区会直接覆盖到真实图片上。

## 5. 后续演进

- 接入真实 `requirement-prototype/manifest.json`、图片帧、layer-map，实现 Figma 导出原型热点覆盖。
- 增加 CLI 执行器，把 CR 文件自动交给 Codex / Cursor / Claude CLI。
- 增加变更影响分析：判断是否需要同步 PRD、feature spec、UI spec、prototype input、baseline。
- 增加 Figma MCP 或 Figma Make prompt 更新链路，减少用户手动复制 prompt 的次数。
