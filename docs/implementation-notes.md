# SSF Autodesign 实现问题沉淀

> 本文档记录当前 MVP 实现过程中遇到的问题、判断和解决方案。后续继续做 Figma 导出、CLI 执行器、真实原型热点时，优先补充这里。

## 1. product-spec 与 prototype-input 的边界

问题：一开始容易把 `prototype-input/` 当成唯一输入源，但它本质是给 Figma Make / Motiff / Uizard 等原型工具看的派生包，不是产品事实源。

解决：后端 `SpecGraphService` 明确把 `product-spec/` 作为产品设计事实源，把 `prototype-input/` 作为页面、组件、标注和原型入口源。当前 MVP 中：

- `product-spec/06-feature-task-spec.md` 提供 `FEAT / BR / AC`。
- `prototype-input/02-screen-contracts.md` 提供 `SCR / CMP`。
- `prototype-input/06-ui-annotation-handoff.md` 提供 `PANN / PFLOW` 以及视觉层与产品事实的连接。

## 2. 不维护 ai-session，改用一次性 CR 上下文

问题：如果用户在系统里修改需求，但真正执行修改的是 Codex / Cursor / Claude CLI，原来的对话上下文往往已经丢失。

解决：系统不维护长期 AI 会话目录，而是在每次点击原型块并提交修改意图时生成 `change-requests/CR-xxx.md`。这个文件必须自带：

- 用户修改意图。
- 被点击的 `SCR / CMP`。
- 相关 `FEAT / BR / AC`。
- 相关 `PANN` 标注。
- 给 CLI Agent 的执行提示词。

这样后续任意 CLI Agent 都可以从零读取 CR 文件完成推理。

## 3. Markdown 解析策略

问题：Skill 产物是 Markdown，不是 JSON。直接用通用 Markdown AST 会增加复杂度，而第一版只需要读固定表格。

解决：当前解析器采用窄契约解析，只识别 Skill 已稳定输出的标题和表格格式，例如：

- `## 1. SCR-001 ...`
- `| SCR-001 | ... |`
- `| CMP-203 | ... |`
- `# FEAT-001 ...`
- `| BR-005 | ... |`
- `| AC-001 | ... |`

注意：如果 Skill 后续调整表格列顺序，需要同步更新 `SpecGraphService`，或者把 Skill 产物升级为结构化 JSON/YAML。

## 4. 编码与 BOM 问题

问题：最初有 Java 文件带 UTF-8 BOM，Maven 编译时报 `非法字符: '\ufeff'`。这类问题和之前 Skill 被反馈“读取乱码”是同一类风险。

解决：

- 所有新写源码和配置统一无 BOM UTF-8。
- 验证时增加 BOM 扫描。
- Markdown 读取固定使用 `StandardCharsets.UTF_8`。

## 5. Maven 私有源与代理

问题：本机 Maven 默认配置指向一个不可用的私有 Nexus，导致 Spring Boot parent POM 下载失败。

解决：

- 项目内增加 `backend/.mvn/settings.xml`，让本项目显式使用 Maven Central。
- 在需要下载依赖时支持使用本机代理 `127.0.0.1:7890`。

## 6. TypeScript 构建副产物

问题：`vue-tsc -b` 在未设置 `noEmit` 时会把 `.js` 文件发射到 `frontend/src`，造成源码目录混乱。

解决：

- `frontend/tsconfig.json` 增加 `noEmit: true`。
- `package.json` 构建脚本使用 `vue-tsc --noEmit && vite build`，避免 `vue-tsc -b` 生成 `tsconfig.tsbuildinfo`。
- 清理已生成的 `frontend/src/**/*.js`。
- `.gitignore` 增加 `frontend/src/**/*.js` 和 `frontend/tsconfig.tsbuildinfo`。

## 7. 前端依赖版本

问题：`lucide-vue-next` 安装时提示 deprecated。

解决：切换为官方推荐的 `@lucide/vue`，并更新所有 import。

## 8. Docker 与 Windows PATH

问题：Docker 安装后当前 PowerShell 没刷新 PATH，直接运行 `docker` 会失败；只运行 `docker.exe` 又可能找不到 `docker-credential-desktop`。

解决：

- README 中提示重新打开终端。
- 临时验证时把 `C:\Program Files\Docker\Docker\resources\bin` 加到当前命令 PATH。

## 9. 容器模式下的本地路径

问题：后端需要读取并写入 Windows 本地 `ssf-workspace`。如果后端在容器内运行，`C:\...` 路径不可直接识别。

解决：

- 推荐 MVP 阶段后端在宿主机运行，MySQL 使用容器。
- `docker-compose.yml` 中为 full profile 挂载 `C:/Users/funjson/Documents:/host/Documents`，容器模式下使用 `/host/Documents/...` 路径。
- 挂载不能只读，因为生成 CR 时需要写入 `change-requests/`。

## 10. 当前技术债

- `SpecGraphService` 仍是 Markdown 窄解析，后续最好由 Skill 直接生成 machine-readable index。
- 真实 Figma 导出的 frame 图片已支持按约定读取，但 layer-map 热点尚未接入。
- Change Request 已生成文件，但还没有自动调用 Codex / Cursor / Claude CLI。
- 当前实体表结构适合 MVP，后续如果要审计每次文件变更，需要增加 `change_request_runs` 或 `file_patch_records`。

## 11. Windows 下运行中 jar 被锁

问题：后端用 `java -jar target/ssf-autodesign-0.1.0.jar` 启动后，再执行 `mvn package` 会在 Spring Boot `repackage` 阶段失败，因为 Windows 不允许重命名正在运行的 jar。

解决：

- 重新打包前先停止监听 `8080` 的后端进程。
- 开发期优先使用 `mvn spring-boot:run`，减少直接锁定打包产物的概率。
- 如果必须用 jar 验证，构建完成后再启动 jar。

## 12. 原型来源标识

问题：用户如果还没有导出 Figma 原型，中间画布只能由 `SCR/CMP` 合成；但如果用户已经提供真实原型，系统应优先使用用户原型，并明确告诉用户当前看到的是什么。

解决：

- 新增 `PrototypeAssetService`，检测 `requirement-prototype/frames/SCR-xxx.png|jpg|jpeg|webp|svg`。
- `SpecGraphDto` 增加 `prototypeSource` 字段，返回 `user-prototype` 或 `generated-contract-preview`。
- 前端 `PrototypeCanvas` 根据 `prototypeSource` 展示“用户原型”或“系统合成预览”标识。
- 用户原型存在时先展示图片，暂时保留 `CMP` 列表作为可点击需求块；后续接入 `layer-map.json` 后再把热区覆盖到图片上。

## 13. Demo 项目隔离

问题：验证阶段创建的“端到端验证项目”会让用户误以为系统自带真实项目，和用户自己创建项目的路径混在一起。

解决：

- 新增 `ProjectEntity.demoProject` 和 `WorkspacePathEntity.sourceType`。
- 后端启动时通过 `DemoDataInitializer` 只种子化一个 `DailyBrief Horizon 演示项目`。
- Demo workspace 放在独立的 `demo/ssf-workspace` 目录，不再使用 `runtime-test/`。
- 前端把 Demo 放入“我的项目”列表并显示 `DEMO` 标识；Demo 项目只读，不能删除、导入 workspace、上传原型或生成 CR。

## 14. 浏览器无法直接暴露本地目录路径

问题：Web 页面不能像桌面应用一样直接拿到用户选择文件夹的绝对路径，否则浏览器安全模型会被绕过。

解决：

- 前端使用文件夹选择器读取用户选择的文件集合和相对路径。
- 后端 `WorkspaceImportService` 把这些文件复制到 `data/imported-workspaces/project-{id}/...`。
- 系统扫描复制后的托管目录，并在 UI 中标记为“导入副本”。
- 仍保留“高级：粘贴本地路径”入口，方便本机开发或后续桌面壳模式使用。

## 15. Java 源码中文编译编码

问题：即使文件本身是 UTF-8，如果 Maven 没有显式指定源码编码，Windows 环境下 javac 可能按系统默认编码编译，导致后端返回的中文提示和 Demo 名称乱码。

解决：

- `backend/pom.xml` 增加 `project.build.sourceEncoding=UTF-8` 和 `project.reporting.outputEncoding=UTF-8`。
- 重新 `clean package` 后用 `curl.exe` 验证 API 原始 JSON，确认中文正常。

## 16. product-spec 任务详情与流程图

问题：第一版右侧栏只展示 FEAT/BR/AC 摘要，无法支撑用户针对一个需求块修改“主流程、分支、异常、权限、输入输出”等细节。

解决：

- `SpecGraphService` 解析 `product-spec/06-feature-task-spec.md` 的 `2.1` 到 `2.15` 小节。
- `FeatureNodeDto` 增加章节、表格、主流程、分支流程、异常流程。
- 前端新增 `FeatureFlowDiagram.vue`，用轻量 CSS/SVG 风格的结构图展示流程，不引入 Mermaid 依赖。

## 17. workspace 导入副本与重复校验

问题：用户通过浏览器选择文件夹时，Web 安全模型不会暴露本地绝对路径；如果强行用内容指纹阻止导入，会误伤“我只是想在不同项目中导入一份副本”的真实场景。

解决：

- “选择 ssf-workspace 文件夹”走导入副本模式：后端复制到 `data/imported-workspaces/...`，系统后续操作这份托管副本。
- “高级：粘贴本地路径”走原始路径模式：后端直接使用规范化后的本地目录。
- 本地路径模式按 `WorkspacePathEntity.path` 做全局重复校验，因为它直接操作同一份真实目录。
- 导入副本模式不再做内容指纹重复校验，因为每次导入都会形成系统托管副本。
- 导入副本的默认显示名使用导入时间，例如 `2026-05-29 17:10 导入`；用户可编辑系统显示名，但不改变真实目录名。
- 删除项目或移除 workspace 只删除数据库可见性，不删除原始文件，也不删除托管副本目录，避免误删。

## 18. 资源管理与文件预览

问题：用户希望在左侧 SPI 下直接查看完整目录树，并在中间区域预览 `txt / markdown / html` 等文件，而不是离开系统去文件管理器里找。

解决：

- 后端新增实例文件树接口和文件内容接口，所有路径都通过实例根目录做 normalize 和 startsWith 校验。
- 预览限制在文本类文件，单文件上限 512KB，避免浏览器一次性加载大文件卡顿。
- HTML 文件使用 sandbox iframe 预览，同时保留源码展开入口。
- 左侧 `资源管理` 只负责切换文件树与中间预览；右侧上下文仍来自当前 SPI 的 `SpecGraph`，方便后续把文件查看、需求块修改和 CR 生成串起来。

## 19. HTML 原型包与 DOM 绑定

问题：墨刀、Axure 等工具导出的 HTML 可能是单文件、ZIP 包，也可能是多层级文件夹；即使能预览，也通常没有 `CMP/FEAT/BR/AC` 需求追踪标识。

解决：

- 后端新增 HTML 原型包导入能力，统一存入 `requirement-prototype/html-packages/current/source/`。
- 支持单 HTML、多文件选择、文件夹选择和 ZIP 包；导入时保留相对路径，并做路径越界校验。
- 入口页识别顺序为 `index.html`、`start.html`、`home.html`、`main.html`、`default.html`，否则选择最短路径的第一个 HTML。
- HTML 文件通过后端代理返回，并动态注入 `prototype-bridge`。Bridge 捕获 hover/click，向父页面发送 DOM selector、文本摘要、页面 ID 和可选组件 ID。
- 前端新增 `HTML 预览` 模式，使用 iframe 展示 HTML，右侧保留产品事实上下文，中间侧栏提供 DOM 到 CMP 的绑定。
- DOM 绑定写入 `requirement-prototype/html-packages/current/layer-map.json`，作为后续“原型块点击 -> 产品设计事实源”的统一连接层。

当前限制：

- HTML 原型 iframe 与主应用跨源，自动化测试工具不一定能直接进入 iframe，但真实用户点击可以通过 `postMessage` 回传。
- 第一版只做手动 DOM 绑定；自动匹配 CMP、生成修正 prompt、图片框选标注留到下一轮。
## 20. requirement-prototype 作为真实原型根目录

问题：如果继续把 HTML 原型、图片原型、Axure/Motiff/墨刀导出包分别放到不同概念目录，后续做统一识别、标注、回写和版本管理会变复杂。

解决：
- 产品概念上可以称为“原型包”，但落盘统一收敛到 `requirement-prototype/`。
- 新的 HTML 原型上传写入 `requirement-prototype/packages/current/source/`。
- `prototype-manifest.json` 和 `layer-map.json` 放在 `requirement-prototype/packages/current/`。
- 保留旧目录 `requirement-prototype/html-packages/current/` 的读取兼容；如果检测到旧目录但新目录不存在，系统会复制一份到新目录，避免已有测试数据失效。
- `prototype-input/` 继续只表示“给 UI 生成工具看的输入包”，不存放用户真实上传或导出的原型资产。

## 21. 原型上传与标注的菜单职责
问题：左侧点击“原型上传”时如果仍然保留“需求预览”的选中态，用户会误以为当前仍在需求预览页面；同时标注页右侧只显示局部上下文，不适合作为标注前的全局任务总览。

解决：
- 前端新增 `prototype-upload` 模式，“原型上传”成为独立入口；上传二级按钮仍使用橙色强调，避免和 SPI 工作模式选中态混在一起。
- 中间上传页统一说明 HTML 文件夹、HTML/ZIP、图片原型三类输入都会整理到 `requirement-prototype/`，减少对某一个测试文件的硬编码感。
- 标注模式右侧改为 `TaskOverviewPanel`，按 SCR 分组展示全部 FEAT，并嵌入主流程、分支流程、异常流程、BR 和 AC，便于用户先从任务总览中选择要绑定或修改的需求块。

补充调整：
- “原型上传”左侧不再展开二级按钮，所有上传操作都放到中间工作区，降低菜单层级复杂度。
- `TaskOverviewPanel` 的 SCR 行改为点击整行展开/折叠，避免行内按钮阻断 `details/summary` 的默认折叠行为。
- 页面文案只描述已实现能力：HTML 包可识别入口页并保留资源；图片包按 `SCR-xxx` 文件名识别页面帧。暂不宣称图片 ZIP、图片文件夹自动解包，或对包内无关文件做语义过滤。

## 22. 标注总览的维度切换

问题：标注页右侧顶部的“页面 / 任务 / 规则 / 验收”四个统计块只有展示作用，用户无法从不同产品事实维度检查当前原型应该绑定哪些内容。

解决：
- `TaskOverviewPanel` 将四个统计块改为可点击的维度入口，默认选中“页面”，保持原有按 SCR 分组的总览。
- “任务”维度按 FEAT 展示用户故事、目标、流程图、关联规则和验收点。
- “规则”维度按 FEAT 分组展示 BR 的触发条件、规则、结果和测试关注点。
- “验收”维度按 FEAT 分组展示 AC 的 Given / When / Then。
- 视觉上仍保留统计卡片样式，但增加选中态，避免和左侧 SPI 菜单层级的选中态混淆。

## 23. HTML 原型浏览与 DOM 标注的模式隔离

问题：HTML 原型需要可交互浏览，也需要点击 DOM 做需求绑定。第一版 bridge 在捕获阶段拦截所有点击，会导致墨刀、Axure 等导出的原型无法进入其他页面或触发原有交互。

解决：
- `HtmlPrototypeCanvas` 增加“浏览 / 标注”分段控制，默认进入“浏览”模式。
- 浏览模式下 iframe 内原型保留原始点击、表单和弹窗行为；如果临时需要选 DOM，可以使用 `Alt / Ctrl / Meta + 点击`。
- 标注模式下点击 DOM 会阻止原型原有跳转，并回传 selector、文本摘要、候选组件等信息给父页面，用于绑定 CMP。
- 后端 `HtmlPrototypeService` 注入的 bridge 支持接收 `ssf-prototype-set-mode` 消息，只有处于标注模式或用户按住修饰键时才执行 `preventDefault` 和 `stopPropagation`。
- 这个方案先解决“真实原型能浏览”和“DOM 能标注”的冲突；后续如果要做更强的自动识别，可以在 manifest/layer-map 中补充页面状态和热点来源。

## 24. ANN 标注编号取代单 DOM 绑定

问题：第一版 `layer-map.json` 是 `BIND -> DOM -> CMP` 的单目标模型，会让用户误以为一个 DOM 只能绑定一个组件，也无法表达“一个标注编号包含多个 DOM”或“同一个 DOM 同时属于多个标注编号”的真实标注场景。

解决：
- `layer-map.json` 升级为 version 3，仍保留 `bindings` 字段名，但只表达新版 ANN 标注集合，不再读取或兼容旧版 `BIND -> 单 DOM -> CMP` 数据。
- 旧 `layer-map.json` 可以直接清理；后端遇到非 version 3 的 layer-map 会返回空标注，避免把历史结构带入新交互。
- App 顶层统一管理 HTML 原型的 `browse / annotate` 模式，避免右侧 ANN 列表点击后 iframe 进入标注态但按钮仍显示浏览态。
- 标注过程改为先维护 `draftTargets`：点击多个 DOM 会形成一个待保存集合，再保存为一个 ANN；再次点击同一 DOM 会从待保存集合移除。
- 一个 ANN 支持多个 DOM target；同一个 DOM 可以被多个 ANN 复用，不做全局冲突判断，只在同一个 ANN 内按 `pagePath + selector` 合并重复 DOM。
- 标注表单合并为一个页面：SCR 必选，CMP 可选；不选 CMP 就是页面级 SCR 标注，选 CMP 就是组件级标注。
- ANN 名称可编辑，iframe bridge 会在原型中绘制持久化标注框与标签，标签格式为 `标注名 (SCR-xxx/CMP-xxx)` 或 `标注名 (SCR-xxx)`。
- 点击右侧已保存 ANN 会高亮该 ANN 的所有 DOM 框，并让右侧任务总览按该 ANN 的 `FEAT / BR / AC` 过滤。

## 25. HTML DOM 标注状态同步必须单源化

问题：HTML 原型标注横跨三层：iframe bridge、`HtmlPrototypeCanvas`、App 顶层状态。前几轮实现里，`draftTargets` 同时存在于 iframe、组件本地状态和 App 状态，`activeHtmlBinding` 也和 `selectedHtmlBindingId` 同时可写，导致出现“原型绿色框变化但待保存 DOM 列表不刷新”以及“ANN 行已选中但右侧标注维度仍为 0”的分叉状态。

解决：
- App 顶层是前端唯一事实源：`draftHtmlTargets` 只在 App 中保存，组件只通过事件请求更新。
- `HtmlPrototypeCanvas` 不再维护本地 `localDraftTargets`，待保存 DOM 列表直接读取 `props.draftTargets`。
- iframe bridge 可以为了即时反馈保留内部临时 `draftTargets`，但每次点击后必须通过 `postMessage` 把完整列表发回父页面；父页面更新后再通过 `ssf-prototype-sync-annotations` 回灌给 iframe。
- `activeHtmlBinding` 改为 computed，只能由 `selectedHtmlBindingId + htmlBindings` 推导，不能手动赋值。
- 浏览模式只做纯展示：已保存 ANN 列表只能点击定位，不显示删除等写操作；标注模式才显示新建、删除、清空等操作入口。
- 浏览模式的 ANN 行使用普通链接 `#ann-ANN-xxx` 作为兜底入口，App 统一监听并轻量轮询 hash，最终都回到 `selectHtmlBinding`。这样即使组件事件链被 iframe、浏览器沙箱或热更新状态影响，刷新和重新进入也能恢复到同一个 ANN 选择。

经验：
- 跨 iframe 的交互不能让每层都“顺手存一份状态”。如果需要即时视觉反馈，可以允许子层短暂乐观渲染，但最终数据必须由父层单源回灌。
- 派生状态优先用 computed，不要再手动维护一份对象副本。
- 凡是“左侧/中间/右侧同时联动”的能力，先画出状态所有权，再写事件，不要从按钮事件开始补。

## 26. 2026-06-02 DOM 标注回归问题复盘

问题表现：
- 标注模式点击同一个 DOM 后，原型内出现两个叠加的绿色选中框。
- “待保存 DOM”列表只显示第一次点击的内容，后续新增、取消、删除、清空都不能稳定同步到原型框。
- `CMP` 下拉菜单在部分场景变空，之前能随 `SCR` 联动。
- “待保存 DOM”块位置漂移到了 ANN 新建块下面，不符合已确认交互顺序。

当前判断：
- 绿色框叠加不是单纯 CSS 问题，主要是 bridge 同时给原始 DOM 加 `data-ssf-draft-target` outline，又额外绘制 `.ssf-annotation-box.draft` 覆盖层；如果目标 DOM 已经属于某个已保存 ANN，还会再叠一层持久化 ANN 框。
- 列表不同步的根因是 `draftTargets` 仍在 iframe bridge 和 App 顶层同时被修改。bridge 点击后先本地 toggle 并渲染，再把完整列表发给父页面；父页面 watcher 又把 props 快照回灌给 bridge。这是“双写状态”，会产生竞态和旧快照覆盖。
- `CMP` 下拉变空和 `screenChoice` 初始化策略有关。外部 HTML 通常没有 `data-screen-id`，点击 DOM 时无法可靠反推 SCR；当前 watch 只在 `screenChoice` 为空时设置 SCR，一旦落到错误页面，后续点击不会自动修正，`availableComponents` 就可能为空。
- 当前模板顺序是 ANN 表单、待保存 DOM、已保存 ANN；需求顺序应是待保存 DOM、ANN 表单、已保存 ANN。

后续修复原则：
- 父页面 App 是唯一事实源：DOM 点击、列表删除、清空都只发“请求”，由 App 统一计算下一份 `draftHtmlTargets`，再完整同步给 iframe。
- iframe bridge 只负责 DOM 命中、selector 生成和视觉渲染；不再自行决定最终 draft 集合。
- 待保存 DOM 与已保存 ANN 必须分离：待保存只联动原型绿色草稿框，已保存 ANN 才联动右侧“标注”维度。
- 浏览模式是纯展示，只保留已保存 ANN 列表的点击定位能力，不提供删除等写操作。
- 修复时先重建状态协议，再调整 UI 顺序；不要继续在局部按钮事件上补丁式修复。

## 27. 已确认的系统需求记忆

项目与工作目录：
- 左侧以项目维度管理，用户创建项目后导入 `ssf-workspace`。
- Web 文件夹选择会创建系统托管副本；高级路径模式才直接使用本地原始目录。
- Demo 项目保留在“我的项目”中，有明显 `DEMO` 标识，不可删除、不可上传、不可写入 CR。
- 删除项目或移除 workspace 只影响系统可见性，不删除用户本地文件。

SPI 工作入口：
- `产品架构管理` 是占位入口，后续再实现。
- `需求预览` 用于核对 product-spec/prototype-input 合成的需求视图，当前只读。
- `原型上传` 统一导入 HTML 文件夹、HTML/ZIP、图片原型，落盘到 `requirement-prototype/`。
- `原型标注` 是核心工作台，用于把真实原型视觉元素绑定到 SCR/CMP/FEAT/BR/AC。

HTML 原型标注：
- 浏览模式保留原型原生交互；标注模式点击 DOM 加入或移出“待保存 DOM”。
- 一个 ANN 可以包含多个 DOM；同一个 DOM 可以被多个 ANN 复用，不做全局冲突判断。
- SCR 必选，CMP 可选；不选 CMP 表示页面级 SCR 标注。
- “待保存 DOM”块应在 ANN 新建块上方；删除单个 DOM 和清空必须与原型绿色框双向联动。
- 已保存 ANN 在标注模式可删除且二次确认；浏览模式只展示和点击，不显示操作按钮。
- ANN 列表联动右侧“标注”页是后续能力，当前优先保证待保存 DOM 与原型框同步稳定。

右侧任务总览：
- 默认展示“页面”维度，可切换“任务 / 规则 / 验收 / 标注”。
- 页面和任务详情必须来自 `product-spec`，尤其是 `06-feature-task-spec.md` 的主流程、分支流程、异常流程、BR、AC。
- 标注维度只展示当前选中 ANN 关联的 SCR/CMP/FEAT/BR/AC，不和待保存 DOM 草稿状态混用。

## 28. DOM 标注协议修复记录

继续修改 HTML 原型标注前，必须先阅读 `docs/READ_BEFORE_HTML_ANNOTATION_CHANGES.md`。

本次修复：
- 受控乐观更新方案已废弃，因为它仍然让 iframe bridge 和 App 顶层同时写 `draftTargets`，会造成原型绿色框与右侧“待保存 DOM”列表分裂。
- 当前恢复为严格父页面单源方案：`App.vue` 的 `draftHtmlTargets` 是唯一事实源。
- iframe bridge 点击 DOM 时只发送 `ssf-prototype-draft-target-click`，不再本地 toggle 最终 draft 集合，也不再发送 `ssf-prototype-draft-targets-change`。
- `HtmlPrototypeCanvas` 收到点击消息后只触发 `toggle-draft-target`；列表删除/清空只触发 `set-draft-targets`；所有变化都由 App 统一计算下一份 `draftHtmlTargets`。
- App 状态变化后，再通过 `ssf-prototype-sync-annotations` 把完整快照回灌给 iframe 绘制绿色框，确保原型框与右侧列表来自同一份数据。
- 移除 `[data-ssf-draft-target="true"]` 的绿色 outline CSS，避免原始 DOM outline 与 overlay 框叠加成双框。
- 渲染已保存 ANN 时，如果同一个 DOM 正在待保存集合里，优先显示草稿框，避免持久框和草稿框重叠。
- 标注面板顺序恢复为：待保存 DOM、ANN 名称/SCR/CMP/新建、已保存 ANN。
- SCR 默认选中当前 SpecGraph 第一个页面，CMP 下拉重新随 SCR 联动展示。

验证结果：
- 前端 `npm run build` 通过。
- 后端健康检查 `/api/health` 正常。
- 返回的 HTML bridge 已包含 `ssf-prototype-draft-target-click`，且不再包含 `toggleLocalDraftTarget`、`ssf-prototype-draft-targets-change` 与旧的 draft outline CSS。
- Codex 内置浏览器无法自动点击跨源 iframe，真实 DOM 点击仍需人工验证。

补充修复：
- 仅使用 `pagePath + selector` 判断同一个 DOM 不稳定，因为用户第二次点击同一视觉块时可能实际命中子元素，导致 selector 改变。
- bridge 现在会给被点击的 DOM 写入 `data-ssf-target-id`，并把 `targetId` 放入 payload 与 draft target。
- bridge 点击时会先检查当前点击元素是否落在已选中 draft DOM 的父子范围内；如果是，则使用原来的 draft DOM 作为本次点击目标，从而支持再次点击取消。
- 前端 `HtmlPrototypeCanvas` 和 App 顶层匹配逻辑改为优先按 `targetId` 判断，selector 只作为兜底。
- 右侧待保存列表的删除和清空会继续通过完整 draft target 快照回灌 iframe，因此应能同步取消原型中的绿色框。
