# SSF Autodesign

SSF Autodesign 是一个面向 AI 软件研发流程的本地产品设计工作台。它把 `ssf-product-pm` Skill 生成的结构化产品设计文档、原型生成输入包、需求原型和后续变更请求连接起来，让用户可以一边看需求和原型，一边把修改意图沉淀回结构化文件。

当前 MVP 重点验证三件事：

- 导入 `ssf-workspace`，读取其中的 SPI 版本、`product-spec`、`prototype-input`、`requirement-prototype` 等产物。
- 在 HTML 原型中做 DOM 级需求标注，把原型块和 SCR/CMP/任务/规则/验收上下文关联起来。
- 为后续 AI 修改产品设计、生成 change request、驱动研发实现提供可追踪数据。

## Docker 演示启动

仓库内置了一个 demo 工作区：

```text
demo/ssf-workspace
```

这个目录包含当前可演示的产品设计文件和原型文件。Docker 启动后，后端会从这份工作区自动初始化一个内置演示项目；数据库数据不需要提前迁移。

一键启动：

```powershell
docker compose up --build
```

如果拉取基础镜像较慢或失败，请先在 Docker Desktop 中配置代理，或在当前终端设置 `HTTP_PROXY` / `HTTPS_PROXY` 后再执行构建命令。

启动后访问：

```text
http://localhost:5173
```

默认只暴露前端端口：

```text
frontend: http://localhost:5173
```

前端容器使用 Nginx 托管静态资源，并把 `/api` 代理到 Docker 内部网络里的后端服务。后端和 MySQL 默认不暴露到宿主机，这样 Windows 和 Mac 上都可以用同一条命令启动，端口冲突也更少。

如果前端端口冲突，可以覆盖端口：

```powershell
$env:FRONTEND_PORT="5174"
docker compose up --build
```

重置演示数据库和容器数据：

```powershell
docker compose down -v
docker compose up --build
```

## 系统架构

```text
frontend/  Vue 3 + TypeScript + Vite
backend/   Java 21 + Spring Boot + Spring Data JPA
mysql/     MySQL 8.4，通过 docker-compose 启动
demo/      内置 demo ssf-workspace，用于 Docker 演示初始化
docs/      系统设计、实现记录、HTML 标注经验记录
data/      本地运行数据和导入副本，默认不提交 Git
```

核心链路：

```text
ssf-product-pm Skill 产物
  -> product-spec 产品设计文件
  -> prototype-input 原型生成输入包
  -> requirement-prototype 用户导入的原型资源
  -> SSF Autodesign 标注和变更工作台
  -> change-requests 结构化变更请求
```

## 技术栈

- 前端：Vue 3、TypeScript、Vite、Nginx 静态托管、Markdown 渲染、iframe 原型标注桥接。
- 后端：Java 21、Spring Boot、Spring Data JPA、MySQL。
- 原型资源：HTML 原型包、图片原型、`layer-map.json` ANN 标注数据。
- 运行方式：本地开发模式或 Docker Compose 演示模式。

## 主要功能

- 项目管理：创建项目、删除项目、导入 `ssf-workspace`。
- SPI 资源管理：查看工作区完整文件树，预览 Markdown、文本、HTML 等文件。
- 需求预览：根据产品设计文件查看页面、组件、任务、规则和验收上下文。
- 原型上传：支持图片、HTML 文件夹、HTML/ZIP 原型包等资源形态。
- HTML 原型标注：在 iframe 中点击 DOM 元素，维护待保存 DOM 集合并保存为 ANN 标注。
- 右侧上下文面板：按页面、任务、规则、验收、标注维度查看结构化产品设计内容。
- Change Request：把用户修改意图写入 `instances/SPI-xxx/change-requests/`。

## Demo 数据说明

`demo/ssf-workspace` 是仓库内置演示资产，不依赖本机绝对路径。Docker 模式下：

- `./demo` 会挂载到后端容器的 `/demo`。
- HTML 标注会写回 `requirement-prototype/packages/current/layer-map.json`，所以演示过程中产生的标注会落到本机 `demo/` 目录。
- `/data` 是容器内应用数据卷，用来保存导入副本、标注和运行数据。
- MySQL 使用 `mysql-data` volume 保存数据库。

如果想恢复内置 demo 到仓库版本，可以使用 Git 丢弃 `demo/` 下的演示改动，或重新 clone 一份仓库。

本地开发时，默认 demo 路径仍然来自：

```text
backend/src/main/resources/application.yml
ssf.demo.workspace-path: ../demo/ssf-workspace
```

## 本地开发启动

如果只需要本地开发，建议使用开发覆盖文件暴露 MySQL 和后端端口：

启动 MySQL：

```powershell
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d mysql
```

启动后端：

```powershell
cd backend
mvn spring-boot:run
```

启动前端：

```powershell
cd frontend
npm install
npm run dev
```

## 开发验证

前端构建：

```powershell
cd frontend
npm run build
```

后端测试：

```powershell
cd backend
mvn test
```

Docker 配置检查：

```powershell
docker compose config
```

开发覆盖配置检查：

```powershell
docker compose -f docker-compose.yml -f docker-compose.dev.yml config
```

## Git 提交约定

不提交以下内容：

- `data/` 本地运行数据和用户 workspace 导入副本。
- `backend/target/`、`frontend/dist/` 等构建产物。
- `frontend/node_modules/` 依赖目录。
- 上传测试产生的 ZIP/RAR 原型包。
- 本地日志和临时运行文件。

内置演示数据保留在 `demo/ssf-workspace`，用于新用户启动后理解系统能力。
