# ANN Save Refresh Repro

访问地址：

```text
http://127.0.0.1:5173/repro/ann-save-refresh/parent.html
```

这个复现专门验证“选择 DOM -> 新建 ANN -> 已保存 ANN 列表动态刷新 -> iframe 草稿框清空”。

## 验证步骤

1. 打开 `parent.html`。
2. 保持“标注”模式。
3. 在 iframe 原型里点击 1-3 个 DOM。
4. 在右侧输入 ANN 名称。
5. 点击“新建 ANN（正确刷新）”。

预期结果：

- “待保存 DOM”变为 0。
- iframe 绿色草稿框消失。
- “已保存 ANN”立即新增一条。
- 调试面板里 `父页面 ANN` 和 `后端 ANN` 数量一致。

## 故障模拟

点击“模拟只写后端不刷新”会故意只写入 localStorage 模拟后端，不更新父页面内存列表。

这个按钮用于复现主系统里疑似的问题：

```text
数据已保存，但当前已保存 ANN 列表没有动态刷新；
切换页面或重新读取后才能看到。
```

点击“从后端重载 ANN”后，如果列表才出现，说明问题在“保存后没有刷新当前 UI 状态”。

## 协议

iframe -> parent:

```text
ssf-repro-ready
ssf-repro-draft-targets-change
ssf-repro-debug-state
```

parent -> iframe:

```text
ssf-repro-set-mode
ssf-repro-sync-state
```

