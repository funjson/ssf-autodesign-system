# HTML 原型标注状态同步说明

> 注意：继续修改 HTML 原型标注前，必须先阅读 `docs/READ_BEFORE_HTML_ANNOTATION_CHANGES.md`。该文件记录了当前故障、错误改法和后续修复前置检查。

## 背景

原型标注里的“待保存 DOM”必须同时影响两个地方：

- iframe 中的绿色草稿标注框。
- 右侧标注面板里的“待保存 DOM”列表。

之前多次修复失败的核心原因是状态被分成了两份：iframe bridge 自己维护一份 `draftTargets`，App 顶层也维护一份 `draftHtmlTargets`。两边都能修改，且又互相同步，容易产生竞态，表现为原型框已经变化，但右侧列表不刷新，或者右侧删除/清空不能取消原型里的绿色框。

## 当前原则

`App.vue` 中的 `draftHtmlTargets` 是唯一事实源。

iframe bridge 不再决定最终的待保存 DOM 集合，只负责：

- 命中用户点击的 DOM。
- 生成 `targetId`、`selector`、文本摘要等 target 信息。
- 发送 `ssf-prototype-draft-target-click` 给父页面。
- 接收父页面完整快照后重新绘制草稿框。

`HtmlPrototypeCanvas.vue` 只负责转发事件：

- 收到 `ssf-prototype-draft-target-click` 后，触发 `toggle-draft-target`。
- 右侧列表删除或清空时，触发 `set-draft-targets`。
- 每次 `props.draftTargets` 改变后，把完整快照通过 `ssf-prototype-sync-annotations` 发回 iframe。

## 不能再回到的方案

不要让 iframe 在点击时自己保存最终 draft 列表，也不要让 iframe 发送 `ssf-prototype-draft-targets-change` 作为事实源。

允许 iframe 短暂保存从父页面同步过来的 `draftTargets`，但它只能用于：

- 判断再次点击已选中区域时应该取消哪个父级 target。
- 根据父页面快照绘制绿色框。

## 验证重点

每次修改这块都要按以下顺序验证：

1. 标注模式点击一个 DOM，绿色框出现，右侧“待保存 DOM”数量变为 1。
2. 再次点击同一个视觉块，绿色框消失，右侧数量回到 0。
3. 连续点击多个 DOM，绿色框数量和右侧列表数量一致。
4. 在右侧删除单个待保存 DOM，对应绿色框消失。
5. 在右侧清空待保存 DOM，所有绿色框消失。
6. CMP 下拉仍然随 SCR 选择展示对应组件。
7. 资源管理、需求预览、原型上传、原型标注入口不受影响。

## 2026-06-02 修复记录

本次修复删除了 iframe bridge 内部的本地 toggle 与 `ssf-prototype-draft-targets-change` 回传逻辑。

新的链路是：

```text
用户点击 iframe DOM
  -> iframe bridge 发送 ssf-prototype-draft-target-click
  -> HtmlPrototypeCanvas 转为 toggle-draft-target
  -> App.vue 更新 draftHtmlTargets
  -> HtmlPrototypeCanvas 根据 props.draftTargets 回灌 iframe
  -> iframe 只按父页面快照绘制绿色框
```

Codex 内置浏览器目前不能自动点击跨域 iframe，因此真实 DOM 点击仍需要人工验证；但可以通过后端返回 HTML 检查确认旧协议已经移除。
