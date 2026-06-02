# 修改 HTML 原型标注前必须先读

本文档记录 HTML 原型标注功能连续修坏后的经验。后续任何人或任何 AI 修改以下文件前，必须先阅读本文档：

- `backend/src/main/java/com/ssf/autodesign/service/HtmlPrototypeService.java`
- `frontend/src/components/HtmlPrototypeCanvas.vue`
- `frontend/src/App.vue`
- 与 HTML 原型标注相关的样式和类型文件

## 当前已经确认的产品需求

HTML 原型标注的最小闭环是：

1. 用户进入“原型标注”。
2. 用户切换到“标注”模式。
3. 用户点击 iframe 中的 DOM 元素。
4. 被点击 DOM 出现绿色草稿选中框。
5. 右侧“待保存 DOM”列表立即出现同一个 DOM。
6. 用户再次点击同一个视觉块，绿色框消失，右侧列表同步移除。
7. 用户在右侧删除单个待保存 DOM，对应绿色框同步消失。
8. 用户在右侧清空待保存 DOM，所有绿色框同步消失。
9. SCR 必选，CMP 可选，CMP 下拉必须随 SCR 联动。
10. “浏览 / 标注”按钮必须真实切换 iframe 行为，不能只切换按钮样式。

如果以上任意一条不成立，就不能认为本功能修复完成。

## 当前故障表现

最近一次修改后出现的问题：

1. 点击 iframe DOM 后，原型内绿色选中框失效。
2. 点击 iframe DOM 后，“待保存 DOM”列表仍然不能稳定联动。
3. “浏览 / 标注”按钮无法可靠切换，页面 UI 状态、右侧面板状态、iframe 内部行为存在不一致。
4. 当前页面可能出现“右侧已有待保存 DOM，但浏览/标注按钮状态或提示文案不一致”的状态分裂。

## 对比改动前后的问题判断

### 改动前

iframe bridge 内部有自己的 `draftTargets`，点击 DOM 时会：

1. 在 iframe 内本地 toggle `draftTargets`。
2. 立即绘制绿色草稿框。
3. 再把完整 draft 列表通过 `postMessage` 发给父页面。

优点：

- 原型里的绿色框反馈比较快。

缺点：

- iframe 和 App 顶层同时维护 draft 状态，是“双写状态”。
- 容易出现绿色框和右侧“待保存 DOM”列表不同步。
- 右侧删除/清空很难保证能反向影响 iframe。

### 改动后

iframe bridge 不再本地 toggle，只发送 `ssf-prototype-draft-target-click` 给父页面。

理论链路是：

```text
iframe 点击 DOM
  -> postMessage 到 HtmlPrototypeCanvas
  -> emit toggle-draft-target
  -> App.vue 更新 draftHtmlTargets
  -> props 回传 HtmlPrototypeCanvas
  -> syncBridgeAnnotations 回灌 iframe
  -> iframe 绘制绿色框
```

问题是：这条链路太长，且没有任何 ack、日志、状态探针。一旦任意环节没走通，iframe 自己不会再画框，右侧列表也不会刷新，所以表现为“点击完全没反应”。

## 这次真正暴露的架构问题

不要再把问题理解成“某一个按钮没绑定好”或“某一个 watcher 没触发”。

这个功能至少有三份状态：

1. App 顶层状态：`draftHtmlTargets`、`htmlInteractionMode`。
2. `HtmlPrototypeCanvas` 组件状态：iframe ref、表单 SCR/CMP、消息监听。
3. iframe bridge 状态：`annotationMode`、`draftTargets`、overlay layer。

当前问题的本质是三者没有一个可验证的同步协议。

仅靠 `postMessage` 单向发送和 Vue watcher 回灌，不足以保证：

- 模式一定同步。
- 点击一定被父页面收到。
- 父页面状态一定回灌给 iframe。
- iframe 一定按最新父页面状态重绘。

## 后续修复必须遵守的规则

### 1. 先设计协议，再改代码

不能继续从某个按钮或某个 watcher 局部修。

下一次修复必须先写清楚状态协议：

- 谁是 draft DOM 的最终事实源。
- iframe 是否允许本地临时状态。
- 父页面收到点击后是否要返回 ack。
- iframe 是根据本地状态画框，还是只根据父页面快照画框。
- 模式切换是否也需要 ack。

### 2. 必须加调试状态面板或日志

修复前应先增加一个开发态可见的最小调试信息，至少显示：

- 当前父页面 `htmlInteractionMode`。
- 当前 iframe bridge `annotationMode`。
- 父页面 `draftHtmlTargets.length`。
- iframe bridge 当前收到的 `draftTargets.length`。
- 最近一次 iframe -> parent 消息类型。
- 最近一次 parent -> iframe 消息类型。

没有这些探针，继续靠肉眼看绿色框会非常容易误判。

### 3. 不能一次同时改多个方向

每次只允许验证一个闭环：

第一步只验证“点击 DOM -> 右侧列表变化”。

这一步稳定前，不要同时做：

- ANN 保存。
- ANN 删除。
- 右侧任务总览联动。
- hash 跳转。
- 浏览模式 ANN 点击定位。
- UI 顺序调整。

### 4. iframe 自动化验证有限

Codex 内置浏览器目前不能稳定自动点击跨源 iframe。后续验证必须承认这个限制：

- 可以自动验证父页面 DOM。
- 可以自动验证后端注入脚本内容。
- 可以自动验证接口和构建。
- 真实 iframe DOM 点击必须由用户人工验证，或者另建同源/测试专用页面。

### 5. 不要把“视觉立即反馈”和“单一事实源”混在一起

如果选择父页面单一事实源：

- iframe 点击后必须等待父页面回灌才画框。
- 必须确保 postMessage、emit、watch、回灌全部可观测。

如果选择 iframe 乐观反馈：

- iframe 可以先画框。
- 但父页面必须回传确认快照。
- iframe 必须能在父页面拒绝或修正状态时覆盖本地状态。

两种方案只能选一种作为主协议，不能混写。

## 当前建议的下一步

下一次真正修复代码前，建议先做一个“调试协议版本”，而不是直接修 UI：

1. 增加开发态调试面板，展示父页面和 iframe 的模式、draft 数量、最近消息。
2. 保持现有 UI 不动，只确认点击 DOM 时 iframe 是否真的发出消息。
3. 确认父页面是否收到消息。
4. 确认父页面 `draftHtmlTargets` 是否变化。
5. 确认父页面是否把快照发回 iframe。
6. 确认 iframe 是否收到快照并重绘。
7. 以上六步全部确认后，再决定采用“父页面单源”还是“iframe 乐观反馈 + 父页面确认”。

## 2026-06-02 调试探针记录

本次已经按上面的要求增加了最小调试状态：

- iframe bridge 会发送 `ssf-prototype-debug-state`。
- `HtmlPrototypeCanvas` 右侧标注管理面板会展示调试状态。
- 调试状态包括父页面模式、iframe 模式、父页面 draft 数量、iframe draft 数量、最近一次 `iframe -> parent` 消息、最近一次 `parent -> iframe` 消息、bridge reason。

当前已验证：

- 进入 HTML 原型标注后，调试面板可见。
- 浏览模式下，父页面模式和 iframe 模式都显示 `browse`。
- 点击“标注”后，父页面模式和 iframe 模式都显示 `annotate`。
- 后端返回的 bridge 脚本包含 `ssf-prototype-debug-state`、`ssf-prototype-draft-target-click`、`ssf-prototype-set-mode`，不包含旧的 `ssf-prototype-draft-targets-change`。

下一次人工点击 iframe DOM 时，优先观察调试面板：

- 如果 `iframe -> parent` 没有变成 `ssf-prototype-draft-target-click`，说明 iframe 内点击监听或命中条件有问题。
- 如果 `iframe -> parent` 已经变成 `ssf-prototype-draft-target-click`，但父页面 draft 仍然是 0，说明 `HtmlPrototypeCanvas -> App.vue` 的事件链路有问题。
- 如果父页面 draft 已经变为 1，但 iframe draft 仍然是 0，说明 App 回灌 iframe 的 `ssf-prototype-sync-annotations` 链路有问题。
- 如果父页面 draft 和 iframe draft 都变为 1，但绿色框没出现，说明 iframe 的 `findTarget` 或 overlay 绘制逻辑有问题。

## 2026-06-02 父页面回灌断点定位

人工点击 DOM 后观察到：

- `iframe -> parent` 已经变成 `ssf-prototype-draft-target-click`。
- `父页面 draft` 已经从 0 变成 1。
- 右侧“待保存 DOM”列表也已经显示 1 条。
- `iframe draft` 仍然是 0。
- `parent -> iframe` 停留在 `ssf-prototype-set-mode`，没有变成 `ssf-prototype-sync-annotations`。

结论：

点击监听、iframe 到父页面消息、`HtmlPrototypeCanvas -> App.vue` 的 toggle 链路是通的；断点在“父页面 draft 更新后，没有把完整快照回灌给 iframe”。

本次针对该断点做了最小修复：

- 在 `HtmlPrototypeCanvas.vue` 增加 `draftTargetSignature`，用稳定签名监听 draft 集合变化，而不是只监听数组引用。
- 收到 `ssf-prototype-draft-target-click` 后，除了触发 `toggle-draft-target`，还延迟调用两次 `syncBridgeAnnotations()`，避免 Vue 父子 prop 更新和 postMessage 回灌之间错过时机。

下一次人工点击 DOM 后优先检查：

- `parent -> iframe` 是否变成 `ssf-prototype-sync-annotations`。
- `iframe draft` 是否从 0 变成 1。
- 如果这两个都成立但绿色框还不显示，再进入 `findTarget` / overlay 绘制逻辑排查。

## 2026-06-02 父页面 overlay 方案

继续排查发现，让 iframe 维护 `iframe draft` 并负责绘制绿色框，会把一个简单的“内存列表 + 展示框”需求变成跨 iframe 双向同步问题。因此当前尝试改为父页面 overlay：

- iframe bridge 只负责监听点击、生成 selector、生成 `targetId`，并把 DOM 的 `getBoundingClientRect()` 坐标作为 `rectX / rectY / rectWidth / rectHeight` 发给父页面。
- App 顶层仍然维护唯一 `draftHtmlTargets`。
- `HtmlPrototypeCanvas` 直接根据 `props.draftTargets` 在 iframe 外层的 `.parent-draft-overlay` 中绘制绿色框。
- 删除单个待保存 DOM、清空待保存 DOM、再次点击取消，都只需要改变父页面这一份列表，绿色框会自然跟随列表变化。

此方案下，`iframe draft` 不再是判断绿色框是否成功的关键指标。优先观察：

- `父页面 draft` 是否变化。
- `父页面框` 是否变化。
- 右侧“待保存 DOM”列表是否和 `父页面框` 一致。

如果 `父页面 draft = 1` 但 `父页面框 = 0`，优先检查 target 是否缺少 rect 坐标，或 `pagePath` 是否与当前入口页不一致。

## 2026-06-02 父页面 overlay 第二轮修复

人工验证父页面 overlay 后，新的故障集中在三处：

1. 进入标注模式后，左侧菜单按钮会像被“卡住”，例如资源管理无法正常切换。
2. 点击同一个视觉块后，绿色框可以出现，但再次点击不能稳定取消。
3. 右侧“待保存 DOM”的删除和清空没有稳定驱动父页面 overlay 消失。

本次修复仍然坚持父页面单一事实源：

- `App.vue` 增加 `leaveHtmlAnnotationDraftState()`，凡是离开 HTML 原型标注视图时，都统一切回 `browse`、清空 `draftHtmlTargets`、清空选中元素和选中 ANN，避免左侧菜单切换后仍残留标注态。
- `htmlTargetMatches()` / `targetMatches()` 不再只依赖 `targetId` 和 `selector`，增加 rect 重叠判断。因为用户肉眼点击的是同一个视觉块，但浏览器命中的可能是外层 `div`、内层文字或图标，selector 不一定一致。
- “待保存 DOM”列表项使用完整 `targetKey()` 作为 Vue key，避免列表复用旧 DOM 行导致看起来没有刷新。
- 删除单个待保存 DOM 和清空待保存 DOM 的按钮加 `stop/prevent`，并继续只通过 `set-draft-targets` 更新父页面草稿列表。

后续验证时优先看四个数字/现象是否一致：

```text
父页面 draft 数量
父页面框数量
右侧待保存 DOM 数量
绿色 overlay 框数量
```

这四者应该同步变化。`iframe draft` 在父页面 overlay 方案里不再作为绿色框成功与否的核心指标。

## 2026-06-02 父页面绿色框直连取消

继续人工验证后确认：

- 第一次点击 iframe DOM 可以加入父页面列表，`父页面 draft = 1`、`父页面框 = 1`。
- 因此 iframe 点击监听和 `HtmlPrototypeCanvas -> App.vue` 首次链路是通的。
- 后续问题集中在“取消”路径，而不是“添加”路径。

本次进一步收窄协议：

- 绿色框由父页面 overlay 绘制，所以绿色框本身在标注模式下也可以点击。
- 点击已选中的绿色框时，不再重新走 iframe 点击命中和 selector/targetId 匹配，直接调用 `removeDraftTarget(target)` 从父页面 `draftTargets` 删除。
- overlay 空白区域仍然 `pointer-events: none`，不会挡住用户继续点击 iframe 中的新 DOM。
- 浏览模式下绿色框不可点击，避免阻断原型正常浏览。
- 调试状态增加“草稿操作”，用于区分 `iframe-click`、`remove-to-0`、`clear-to-0` 是否真的触发。

如果用户反馈仍然不能删除或清空，下一步不要先改匹配算法，先看“草稿操作”：

- 没变化：说明按钮或绿色框点击事件没触发。
- 变成 `remove-to-0` / `clear-to-0` 但父页面 draft 仍是 1：说明 `set-draft-targets` 到 App.vue 的事件链路断了。
- 父页面 draft 变 0 但绿色框仍在：说明 `visibleDraftTargets` 或 overlay 渲染没有跟随 props 更新。

### 2026-06-02 绿色框点击不到的处理

人工验证出现：

- 绿色框可见。
- `父页面 draft = 1`、`父页面框 = 1`。
- 点击绿色框后，“草稿操作”没有变成 `remove-to-0`，仍停留在 `iframe-click`。

这说明取消动作没有触发父页面点击事件，不是 App 状态更新失败。

原因判断：

- 之前绿色框放在 `.parent-draft-overlay` 全屏容器内。
- 该容器设置了 `pointer-events: none`，虽然子元素设置了 `pointer-events: auto`，但在 iframe 叠层场景下仍可能直接穿透到 iframe，导致看得见的框点不到。

本次处理：

- 删除全屏 `.parent-draft-overlay` 包裹层。
- 将每个 `.parent-draft-box` 直接作为 `.html-frame-stage` 的绝对定位子元素渲染。
- 框外区域天然不被覆盖，可以继续点击 iframe 新 DOM。
- 框本身在 `annotate` 模式下使用 `.cancellable` 接收点击并调用 `removeDraftTarget(target)`。

## 2026-06-02 最终回到 iframe 主导 toggle

继续验证后，父页面绿色框仍然无法稳定接收点击。由此确认：

- “第一次点击加入列表”已经稳定，说明 iframe 到父页面的添加链路没问题。
- “再次点击取消”不应依赖父页面 overlay 命中，因为 iframe 叠层和 `pointer-events` 会让事件路径不可控。
- 之前点击再点击取消之所以可行，是因为 iframe 内部本地维护 `draftTargets` 并本地重画绿色框。

因此最终策略回到更简单的职责划分：

```text
iframe：负责 DOM 点击、draftTargets toggle、绿色框即时显示/取消
父页面：负责展示待保存 DOM 列表、保存 ANN、右侧删除/清空时把新列表同步回 iframe
```

本次代码调整：

- `HtmlPrototypeService.java` 中 bridge 恢复 `toggleDraftTarget()`。
- iframe 点击 DOM 时先本地 toggle、调用 `renderAnnotationOverlays()`，再发送 `ssf-prototype-draft-targets-change` 给父页面。
- `HtmlPrototypeCanvas.vue` 收到 `ssf-prototype-draft-targets-change` 后直接 `set-draft-targets`，不再二次 toggle。
- 删除父页面 `.parent-draft-box` overlay 相关模板和 CSS。
- 右侧删除/清空仍通过 `ssf-prototype-sync-annotations` 把父页面列表回灌给 iframe。

后续验证标准：

- 第一次点击 DOM：`iframe draft = 1`、`父页面 draft = 1`、草稿操作显示 `iframe-toggle-to-1`。
- 再次点击同一个 DOM：`iframe draft = 0`、`父页面 draft = 0`、草稿操作显示 `iframe-toggle-to-0`。
- 右侧删除/清空：父页面 draft 变 0 后，iframe 收到同步并重画，`iframe draft` 也应变 0。

## 2026-06-02 多 DOM 选择和模式切换修复

回到 iframe 主导 toggle 后，继续验证出现：

- 点击和再次点击取消已经恢复。
- 但连续点击多个 DOM 时，右侧“待保存 DOM”列表始终只显示第一个。
- 浏览/标注按钮以及左侧资源管理等切换按钮在标注态后不稳定。

本次定位到两个原因：

1. iframe 的 `canonicalClickTarget()` 使用 `node.contains(el) || el.contains(node)`，如果第一次选中了较大的容器，后面点击容器里的子 DOM 会被强行归一为第一个 DOM。
2. 父页面 `uniqueHtmlTargets()` / `uniqueTargets()` 之前用 rect 重叠判断去重，父子 DOM 会因为重叠被当成同一个 target 丢掉。

本次处理：

- `canonicalClickTarget()` 改为只在 `node === el` 时归一，不再用包含关系吞掉子 DOM。
- 父页面 target 去重改回只按 `targetId` 或 `selector` 精确匹配，不再使用 rect 重叠去重。
- `ssf-prototype-draft-targets-change` 到达父页面后，会立即把同一份 target 快照回灌给 iframe，避免 debug 和列表状态漂移。
- 调试状态增加 `lastDraftChangeCount`、`lastSyncDraftCount`、`lastToggleSelector`。
- 浏览/标注按钮和左侧实例切换按钮改为 `pointerdown` 触发，减少标注态下 click 链路被干扰的概率。

后续验证重点：

- 连续点击不同 DOM，`父页面 draft` 和右侧列表数量应该递增。
- 如果点击了父容器后又点击子元素，子元素应能作为新的待保存 DOM 出现。
- 浏览/标注按钮点击后，`父页面模式` 和 `iframe 模式` 应同步变化。
- 左侧资源管理点击后应离开 HTML 标注视图。

## 2026-06-02 最小复现验证通过

新增独立复现：

```text
frontend/public/repro/iframe-annotation-toggle/parent.html
frontend/public/repro/iframe-annotation-toggle/child.html
```

人工验证结果：

- iframe 内连续点击多个 DOM，父页面列表可以递增。
- iframe 内再次点击同一 DOM，可以取消。
- iframe 点击后，父页面左侧按钮和浏览/标注按钮仍可切换。

这证明浏览器、iframe、`postMessage` 这条技术路线本身可行。主系统问题来自实现复杂度，而不是方案不可行。

复现的稳定协议是：

```text
iframe -> parent:
  child-ready
  draft-targets-change
  child-debug

parent -> iframe:
  set-mode
  sync-draft-targets
```

主系统应向复现收敛：

- iframe 是 DOM 点选和草稿绿色框的唯一事实源。
- iframe 每次 toggle 后发送完整 `draftTargets`。
- 父页面收到 iframe 的完整 `draftTargets` 后，只更新右侧列表，不立刻反向回灌。
- 只有右侧删除、清空、保存 ANN、切换入口页、加载保存 ANN 这类父页面主动变更，才向 iframe 发送同步快照。
- 父页面去重只能按 `targetId` / `selector` 精确匹配，不能用包含关系或 rect 重叠吞掉父子 DOM。

## 禁止事项

- 禁止在没有读本文档的情况下修改 HTML 标注相关代码。
- 禁止一边修 DOM 点击，一边改 ANN 列表、右侧任务联动或菜单结构。
- 禁止没有状态探针就继续猜测 watcher 或 postMessage 是否生效。
- 禁止再次引入多个地方同时写最终 `draftTargets`，除非协议里明确 iframe 只是临时乐观态，并且能被父页面确认快照覆盖。
