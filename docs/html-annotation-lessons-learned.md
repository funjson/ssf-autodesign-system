# HTML 原型标注反复修复经验沉淀

本文记录 2026-06 初围绕 HTML 原型标注能力反复调试后的经验。后续修改 `HtmlPrototypeCanvas.vue`、`App.vue`、`HtmlPrototypeService.java` 中的标注逻辑前，应先阅读本文。

## 一句话结论

HTML 原型标注不是一个普通表单功能，它横跨主应用、Vue 组件、iframe 内注入脚本、后端 `layer-map.json`。如果没有明确状态归属和同步协议，很容易出现“数据已保存但页面不刷新”“绿色框和待保存列表不同步”“ANN 选中后全页面按钮失效”这类问题。

最终稳定下来的原则是：

```text
按动作归属单写状态，跨 iframe 只传完整纯对象快照。
```

## 已验证的核心需求

1. 标注模式下点击 iframe 中的 DOM，可以加入待保存 DOM 集合。
2. 再次点击同一个 DOM，可以从待保存 DOM 集合移除。
3. 待保存 DOM 列表中删除单个 DOM，要同步移除原型里的绿色框。
4. 待保存 DOM 列表中清空全部 DOM，要同步清空原型里的绿色框。
5. 一个 ANN 可以包含多个 DOM。
6. 同一个 DOM 可以被多个 ANN 复用，不做全局冲突限制。
7. 新建 ANN 后要保存数据并刷新 ANN 列表，但不应自动进入 ANN 选中态。
8. 已保存 ANN 的选中、高亮、删除，不能污染待保存 DOM 的草稿状态。
9. 浏览模式下只展示已保存 ANN，不提供删除等写操作。
10. 标注模式下才提供新建 ANN、删除 ANN、待保存 DOM 删除和清空。

## 反复出问题的根因

### 1. draft DOM 和 ANN 被混成一条状态链

早期实现里用一个同步消息同时携带：

```text
draftTargets
annotations
activeAnnotationId
```

这样会导致三个动作互相污染：

- 用户删除待保存 DOM，本来只应该影响绿色草稿框。
- 用户新建 ANN，本来只应该影响已保存 ANN 列表和持久化数据。
- 用户选中 ANN，本来只应该影响已保存 ANN 高亮和右侧上下文。

一旦这三类状态走同一条回灌链路，保存或选中 ANN 后就可能覆盖 draft，或者 draft 删除时触发 ANN active 状态变化。

### 2. iframe 和 App 同时写同一份 draftTargets

我们尝试过 App 单一事实源，也尝试过 iframe 乐观更新。真正的问题不是“谁一定是唯一事实源”，而是某一次用户动作不能有两个地方同时决定结果。

当前稳定做法是按动作归属：

- 用户在 iframe 里点击 DOM：iframe 负责命中、toggle、绘制即时反馈，并把完整 draft 快照发给父页面。
- 用户在右侧待保存 DOM 列表删除或清空：App 负责计算新 draft 快照，并把完整快照同步回 iframe。
- 保存 ANN：App 调后端写入，成功后刷新 bindings，清空 draft。
- 选中 ANN：App 只更新 `selectedHtmlBindingId`，并同步已保存 ANN 高亮，不修改 draft。

### 3. postMessage 不能直接传 Vue 响应式对象

这是这轮最关键的技术发现。

`props.draftTargets` 和 `props.bindings` 里的对象可能是 Vue 响应式代理。直接把它们放进 `iframe.contentWindow.postMessage(...)`，非空数组时可能触发结构化克隆失败或同步失败。

这个问题很隐蔽，因为空数组可以正常发送，所以现象会像这样：

```text
删除最后一个 DOM / 清空全部 DOM 可以同步
删除多个 DOM 中的某一个不同步
```

修复规则：

- 所有发给 iframe 的 `draftTargets` 必须先转普通 JSON 对象。
- 所有发给 iframe 的 `bindings / annotations` 也必须先转普通 JSON 对象。
- 不要把 Vue props、computed 结果、reactive/ref 内部对象原样 postMessage。

### 4. pointerdown + confirm 很容易制造交互假死

ANN 删除按钮曾经绑定在 `pointerdown` 上，并在处理函数里直接调用 `window.confirm`。这类组合容易让 pointer/click 状态停在半截，表现为确认后页面按钮失效、状态看起来像卡住。

当前规则：

- 普通按钮写操作优先使用 `click`。
- `pointerdown` 只用于必须抢在 iframe 或拖拽前响应的控件，例如模式切换、拖拽调整宽度。
- `confirm` 不要放在复杂 pointer 链路里。

### 5. 保存 ANN 后不应该自动选中 ANN

自动选中看起来友好，但实际会立即触发 ANN active 同步、右侧上下文联动、iframe 高亮回灌。对用户来说，新建 ANN 后最重要的是确认已保存并清空 draft，而不是立刻进入 ANN 浏览/定位状态。

当前规则：

```text
保存 ANN 成功
  -> 更新 bindings
  -> 清空 draftTargets
  -> 不自动设置 selectedHtmlBindingId
```

用户需要查看某个 ANN 时，再显式点击已保存 ANN 列表。

## 当前稳定协议

### iframe 到父页面

```text
ssf-prototype-draft-targets-change
```

用于 iframe 内 DOM 点击后的完整 draft 快照回传。父页面收到后只更新 `draftHtmlTargets` 和当前选中 DOM 信息。

### 父页面到 iframe

```text
ssf-prototype-sync-draft-targets
```

只同步待保存 DOM 草稿集合。用于右侧删除单个 DOM、清空 DOM、保存 ANN 后清空 draft。

```text
ssf-prototype-sync-ann-annotations
```

只同步已保存 ANN 和 active ANN。用于新建 ANN、删除 ANN、选中 ANN 后更新持久化标注框。

```text
ssf-prototype-set-mode
```

只同步 browse / annotate 模式，不携带业务状态。

## 后续修改守则

1. 不要把 draft DOM、saved ANN、active ANN 再合并成一条大同步消息。
2. 不要在同一次用户动作里让 App 和 iframe 同时计算下一份 draft。
3. 不要直接 postMessage Vue 响应式对象。
4. 不要为了修 ANN 列表刷新去改 DOM 点击协议。
5. 不要为了修待保存 DOM 删除去改 ANN 右侧联动。
6. 每次只验证一个闭环：
   - DOM 点击闭环。
   - 待保存 DOM 删除/清空闭环。
   - ANN 新建刷新闭环。
   - ANN 删除刷新闭环。
   - ANN 选中高亮闭环。

## 手工验收清单

每次改完 HTML 标注相关逻辑，至少手工验证：

1. 切到标注模式，点击 2 个以上 DOM，绿色框和待保存 DOM 列表数量一致。
2. 在待保存 DOM 列表中删除第一个，原型里对应绿色框消失，其他绿色框保留。
3. 在待保存 DOM 列表中继续删除到最后一个，最后一个绿色框消失。
4. 重新选择多个 DOM，点击清空，所有绿色框消失。
5. 输入 ANN 名称并新建，ANN 列表立即出现新记录，页面按钮仍可点击。
6. 新建 ANN 后不会自动跳到 `#ann-xxx` 或自动选中 ANN。
7. 刷新页面后 ANN 仍存在。
8. 删除 ANN，二次确认后列表立即移除，页面不出现持续“处理中”。
9. 浏览 / 标注按钮仍能切换。
10. 左侧资源管理、需求预览、原型上传、原型标注入口仍能切换。

## 调试时优先看这些字段

调试面板里优先观察：

- 父页面模式
- iframe 模式
- 父页面 draft
- iframe draft
- 父页面 ANN
- iframe ANN
- 选中 ANN
- iframe -> parent
- parent -> iframe
- draft ids
- ANN ids
- save 状态 / save 详情
- delete 状态 / delete 详情
- postMessage error

如果 `postMessage error` 出现内容，优先检查是否又把响应式对象传进 iframe。

## 经验提醒

这个功能的难点不是“写一个删除按钮”，而是跨 iframe 的状态同步。局部补丁容易把已修好的功能重新修坏。以后遇到类似问题，先画状态归属和消息协议，再改代码。
