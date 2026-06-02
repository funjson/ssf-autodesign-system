# Iframe Annotation Toggle Repro

访问地址：

```text
http://127.0.0.1:5173/repro/iframe-annotation-toggle/parent.html
```

这个复现用于隔离验证 HTML 原型标注的两个问题：

1. iframe 内连续点击不同 DOM 后，父页面待保存 DOM 列表是否能递增。
2. iframe 内进入标注并点击 DOM 后，父页面按钮是否还能切换。

## 验证步骤

1. 打开 `parent.html`。
2. 保持“标注”模式。
3. 在 iframe 里依次点击：
   - 外层卡片
   - 卡片标题
   - 卡片正文
   - 嵌套分组
   - 嵌套按钮
4. 观察右侧“待保存 DOM”数量是否递增。
5. 再次点击其中一个已选 DOM，观察是否取消。
6. 点击父页面“浏览 / 标注”按钮，观察父页面模式和 iframe 模式是否同步。
7. 点击左侧“资源管理 / 需求预览 / 原型标注”，观察是否能切换。
8. 点击右侧“删除 / 清空”，观察 iframe 绿色框是否同步移除。

## 协议

iframe -> parent:

```text
child-ready
draft-targets-change
child-debug
```

parent -> iframe:

```text
set-mode
sync-draft-targets
```

## 判断

如果这个复现是稳定的，说明浏览器事件模型和 postMessage 协议本身可行，主系统问题在 Vue 组件或 bridge 实现细节。

如果这个复现也出现列表只保留第一个或父页面按钮失效，说明要重新设计原型标注交互，不应继续在当前 iframe 注入方案上修补。
