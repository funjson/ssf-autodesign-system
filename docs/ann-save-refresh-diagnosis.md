# ANN 保存刷新问题诊断

## 当前现象

用户在 HTML 原型标注页中：

1. 进入标注模式。
2. 点击 DOM，待保存 DOM 列表正常出现。
3. 输入 ANN 名称并点击“新建 ANN”。
4. 数据已经写入 `layer-map.json`，重新进入页面可以看到。
5. 但当前页面的“已保存 ANN”列表有时没有立即刷新。

## 需要分开的两个状态

不要再把这两个问题混在一起修：

```text
待保存 DOM：
  iframe 点击后产生的临时 draftTargets。
  当前以 iframe 发送完整 draftTargets 快照为准。

已保存 ANN：
  保存后的 bindings / layer-map.json。
  当前以后端 layer-map.json 为持久化来源。
```

## 最小复现

新增复现页：

```text
http://127.0.0.1:5173/repro/ann-save-refresh/parent.html
```

它提供三个关键按钮：

- `新建 ANN（正确刷新）`：写入模拟后端、刷新父页面 ANN 列表、清空 draft、同步 iframe。
- `模拟只写后端不刷新`：只写 localStorage，不刷新父页面 ANN 列表，用于复现“切换后才看到”的症状。
- `从后端重载 ANN`：从 localStorage 重读 ANN 列表。

如果主系统表现接近“模拟只写后端不刷新”，说明保存成功后当前组件状态没有被刷新。

如果主系统父页面 ANN 已经变化，但列表 DOM 没变，说明渲染区域或条件分支有问题。

如果主系统父页面 ANN 和列表 DOM 都变化，但 iframe draft 没清空，说明这是 iframe 草稿同步问题，不是 ANN 列表刷新问题。

## 主系统新增调试字段

`HtmlPrototypeCanvas.vue` 调试面板增加：

- 父页面 ANN
- 选中 ANN
- iframe ANN
- ANN ids
- draft ids

修复前先观察这些字段：

```text
父页面 ANN 是否增加？
ANN ids 是否出现新 ID？
已保存 ANN DOM 行是否出现新记录？
iframe ANN 是否收到同步？
父页面 draft / iframe draft 是否在保存后清空？
```

## 修复原则

1. 不要重新引入“父页面和 iframe 同时 toggle 同一个 draftTargets”的双写逻辑。
2. 待保存 DOM 列表问题只看 draftTargets 链路。
3. 已保存 ANN 列表问题只看 bindings / layer-map 链路。
4. 保存后是否清空 iframe 草稿，是第三个问题，不能和前两个混修。

