# 企业级 AI BI / NL2SQL 平台

这是系统设计 Oracle 版对应的可运行全 Java MVP。默认使用 H2、确定性 Mock AI 和内存知识检索，无需安装 Ollama、Qdrant 或 Oracle 即可验证主要业务闭环；真实环境使用 Oracle 19c 与 Qdrant，聊天和向量模型可在 Ollama 本地模型与千问官方 API 之间切换。

## 项目结构

- `server`：Spring Boot 后端，含认证授权、知识、语义、NL2SQL、SQL 安全、查询、报告、反馈和审计。
- `web`：Vue3 用户门户与管理员工作台，按路由、页面、组件、状态、服务和类型分层。
- `test-data`：DDL、数据、业务说明、指标、关系、样例 SQL 和黄金问题；其中 `foundry360` 是 25 表、2359 行的晶圆代工综合测试包。
- `deploy`：CentOS 7 离线部署配置模板。
- `docs`：配置、依赖、测试和迁移说明。
- `offline`：Maven 3.9.11 与项目专用离线仓库，支持内网二次开发和严格离线构建。

## 本机 Mock 验证

```text
cd server
mvn spring-boot:run

cd web
npm install
npm run dev
```

打开 `http://localhost:5173`，接口文档为 `http://localhost:8080/swagger-ui.html`。

本机验收账号的统一初始密码为 `Aibi@123`：

| 用户名 | 可访问模块 |
|---|---|
| `question_user` | 数据问答 |
| `report_user` | 数据问答、智能报告 |
| `ai_admin` | 数据问答、智能报告、AI 训练中心 |

这些账号仅用于本机和隔离测试环境，迁移到生产前必须修改密码或替换为企业统一认证。代码目录与权限扩展方式见 `docs/代码结构与登录权限说明.md`。

训练中心使用顺序及 Mock/真实模式差异见 `docs/AI训练中心使用说明.md`。

管理员可在“AI 训练中心 → 模型配置”独立切换聊天模型和向量模型。千问官方 API Key 只通过后端环境变量 `DASHSCOPE_API_KEY` 配置，前端不会录入、保存或回显。本机可使用被 Git 忽略的 `runtime/qwen-api-secret.ps1`，详细方法见 `docs/配置说明.md`。

本机千问官网聊天、向量、Qdrant 重建和 Oracle 19c 查询的真实验证结果见 `docs/千问官网模型验证记录.md`。

数据问答结果确认正确后，可按 Excel、CSV 或 XML 下载本次结果快照；详细流程和接口见 `docs/查询结果下载说明.md`。

智能报告支持查看完整历史内容、下载 PDF/Word，以及按创建人权限删除历史报告；详细规则和接口见 `docs/智能报告历史管理与下载说明.md`。

## 真实内网模式

准备 Oracle 19c 访问账号、Qdrant，以及 Ollama 本地模型或内网 OpenAI-compatible Chat/Embedding 网关后，以 `--spring.profiles.active=real` 启动后端。内网网关方案使用 `deploy/aibi.internal-model.env.example`，Ollama 方案使用 `deploy/aibi.env.example`。平台 Schema 账号需要建表和迁移权限；业务查询建议使用独立只读账号或只读视图。

当前本机真实环境已经配置为 `AIBICDB/AIBIPDB1`。在项目根目录执行：

```text
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\start-local-real.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\verify-local-real.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\smoke-test-real.ps1 -IncludeReport
```

页面：`http://127.0.0.1:5173/`；健康检查：`http://127.0.0.1:8080/actuator/health`；Swagger：`http://127.0.0.1:8080/swagger-ui.html`。完整安装、版本、校验值和离线迁移清单见 `docs/本机真实环境安装与迁移清单.md`。

迁移到完全无外网的 Windows 或 CentOS 7 时，优先直接部署已构建的 JAR 和 `release/web`，目标机无需完整 Maven/npm 缓存；没有 Nginx 可使用项目自带的零依赖 Node.js 静态服务。完整步骤见 `docs/无外网环境部署指南_Windows与CentOS7.md`。
