# SSF Autodesign

SSF Autodesign 是一个面向 AI 软件研发流程的本地产品设计工作台。它把 `ssf-product-pm` Skill 生成的结构化产品设计文档、原型输入包、需求原型和后续变更请求连接起来，让用户可以一边查看需求/原型，一边把修改意图沉淀回结构化文件。

当前版本重点打通两件事：

- 导入 `ssf-workspace`，读取其中的 SPI 版本、`product-spec`、`prototype-input`、`requirement-prototype` 等产物。
- 在 HTML 原型上做 DOM 级需求标注，把原型块和 SCR/CMP/任务/规则/验收上下文关联起来。

## 设计目标

AI 看不懂“纯视觉稿”的业务语义，所以本系统不把原型当作孤立图片，而是把原型作为结构化产品设计文件的可视化入口。

核心思路：

```text
ssf-product-pm Skill 产物
  -> product-spec 产品设计文档
  -> prototype-input 原型生成输入包
  -> requirement-prototype 用户导入的原型资源
  -> SSF Autodesign 标注和变更工作台
  -> change-requests 结构化变更请求
```

未来目标是让用户在原型界面上提出修改意见，再由 AI 根据完整产品设计上下文生成变更请求，并回写到产品设计和研发交付链路中。

## 系统架构

```text
frontend/  Vue 3 + TypeScript + Vite
backend/   Java 21 + Spring Boot + Spring Data JPA
mysql/     MySQL 8.4，通过 docker-compose 启动
demo/      内置只读演示 ssf-workspace
docs/      系统设计、实现记录、HTML 标注经验记录
data/      本地运行数据和导入副本，默认不提交 Git
```

### 前端

前端负责项目树、SPI 实例树、文件预览、需求预览、HTML 原型标注和右侧任务上下文展示。

主要模块：

- `ProjectSidebar.vue`：项目、workspace、SPI、资源入口管理。
- `PrototypeCanvas.vue`：系统合成需求预览和图片原型预览。
- `HtmlPrototypeCanvas.vue`：HTML 原型 iframe 渲染、DOM 标注、ANN 绑定。
- `TaskOverviewPanel.vue`：按页面、任务、规则、验收、标注维度查看产品设计上下文。
- `FilePreview.vue`：Markdown、文本、HTML 等资源文件预览。

### 后端

后端负责 workspace 导入、SPI 扫描、产品设计文档解析、原型资源管理、HTML 原型代理和 ANN 绑定持久化。

主要服务：

- `WorkspaceImportService`：把用户选择的 `ssf-workspace` 导入为系统托管副本。
- `WorkspaceScannerService`：扫描 SPI 实例和 manifest。
- `SpecGraphService`：从 `product-spec` 和 `prototype-input` 构建 SCR/CMP/FEAT/BR/AC 图谱。
- `HtmlPrototypeService`：识别 HTML 原型包、代理 HTML/静态资源、注入标注 bridge。
- `PrototypeAssetService`：管理图片型需求原型资源。
- `ChangeRequestService`：生成结构化变更请求文件。

## 核心功能

### 项目和 Workspace 管理

- 创建和删除项目。
- 导入 `ssf-product-pm` Skill 生成的 `ssf-workspace`。
- 用户导入时创建系统托管副本，不直接修改用户原始目录。
- 演示项目带 `DEMO` 标识，默认只读，不允许删除。
- 工作目录从系统中移除时只删除系统可见性，不删除用户原始文件。

### 产品设计文件读取

当前读取重点包括：

- `product-spec/05-prd.md`
- `product-spec/06-feature-task-spec.md`
- `product-spec/07-ui-ia-screen-inventory.md`
- `product-spec/08-structured-ui-interaction-spec.md`
- `product-spec/09-prototype-prompt-ui-annotation.md`
- `prototype-input/00-07`

系统会把这些文件整理为页面、组件、任务、规则、验收和原型标注上下文。

### 需求预览

如果用户没有导入真实原型，系统会根据结构化 UI/交互规格生成“系统合成预览”，用于快速查看 SCR/CMP 层级和相关需求。

如果用户导入了图片原型，系统会按页面 ID 匹配并展示用户原型。

### HTML 原型标注

HTML 原型支持单文件 HTML、HTML 文件夹、ZIP 解压后的多层级导出目录。系统会识别入口页并通过 iframe 展示。

标注协议遵循一个简单原则：

```text
iframe 负责 DOM 点击、draftTargets toggle、绿色草稿框显示/取消
父页面负责待保存 DOM 列表、SCR/CMP 绑定、ANN 保存、删除和清空
```

稳定消息协议：

```text
iframe -> parent:
  ssf-prototype-ready
  ssf-prototype-draft-targets-change
  ssf-prototype-debug-state

parent -> iframe:
  ssf-prototype-set-mode
  ssf-prototype-sync-annotations
```

复现页面位于：

```text
frontend/public/repro/iframe-annotation-toggle/
```

可访问：

```text
http://127.0.0.1:5173/repro/iframe-annotation-toggle/parent.html
```

这个复现用于验证 iframe、postMessage、多 DOM toggle、父页面按钮切换等基础协议，避免在主系统里盲目修补。

### Change Request

用户在右侧输入修改意图后，系统可以生成结构化 CR 文件，写入：

```text
instances/SPI-xxx/change-requests/CR-xxx.md
```

后续可以把 CR 交给产品设计 AI、架构 AI、前后端研发 AI 继续处理。

## 目录约定

`ssf-workspace` 的典型结构：

```text
ssf-workspace/
  instances/
    SPI-001/
      product-spec/
      prototype-input/
      requirement-prototype/
      change-requests/
      manifest.md
```

HTML 原型包推荐放在：

```text
instances/SPI-xxx/requirement-prototype/packages/current/
```

图片原型推荐放在：

```text
instances/SPI-xxx/requirement-prototype/frames/
  SCR-001.png
  SCR-002.png
```

## 快速启动

### 1. 启动 MySQL

```powershell
docker compose up -d mysql
```

默认数据库配置在 `backend/src/main/resources/application.yml`，可通过环境变量覆盖：

```text
SPRING_DATASOURCE_URL
SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_PASSWORD
SSF_DATA_ROOT
SSF_DEMO_ENABLED
SSF_DEMO_WORKSPACE_PATH
```

### 2. 启动后端

```powershell
cd backend
mvn spring-boot:run
```

后端默认端口：

```text
http://127.0.0.1:8080
```

健康检查：

```text
http://127.0.0.1:8080/api/health
```

### 3. 启动前端

```powershell
cd frontend
npm install
npm run dev
```

前端默认地址：

```text
http://127.0.0.1:5173
```

### 4. 可选：全量 Docker

`docker-compose.yml` 提供了 `full` profile：

```powershell
docker compose --profile full up --build
```

注意：容器内不能直接识别 Windows 的 `C:\...` 路径。当前 compose 将 `C:/Users/funjson/Documents` 挂载为 `/host/Documents`，容器模式下添加 workspace 时需要使用 `/host/Documents/...` 路径。

## 开发验证

前端构建：

```powershell
cd frontend
npm run build
```

后端编译：

```powershell
cd backend
mvn test
```

如果只需要快速编译某个 Java 文件，可使用本地 classpath，但正式验证仍建议使用 Maven。

## Git 提交约定

以下内容不应提交：

- `data/` 本地运行数据和用户 workspace 导入副本。
- `backend/target/`、`frontend/dist/` 构建产物。
- `frontend/node_modules/` 依赖目录。
- 上传测试产生的 ZIP/RAR 原型包。
- 本地日志和临时运行文件。

内置演示数据保留在 `demo/ssf-workspace`，用于新用户启动后理解系统能力。

## 当前状态

这是一个第一版 MVP，重点验证产品设计文件、原型标注和后续 AI 变更请求之间的连接方式。后续还会继续补充：

- 更完整的产品设计文件回写。
- 原型标注和任务/规则/验收的双向联动。
- Figma/Motiff/Uizard 等原型工具导出结构适配。
- 面向研发 AI 的变更包和上下文包生成。
