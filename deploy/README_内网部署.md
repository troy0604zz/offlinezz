# CentOS 7 内网部署说明

Windows 与 CentOS 7 的统一离线部署步骤、Node.js 替代 Nginx、Maven/npm 离线依赖策略及完整搬迁清单见 `docs/无外网环境部署指南_Windows与CentOS7.md`。本页保留 CentOS 7 的关键注意事项。

## 重要兼容性提示

CentOS 7 已停止维护，系统 glibc 较旧。Qdrant 建议在有网的同架构 Linux 机器下载官方 `x86_64-unknown-linux-musl` 静态发布包，连同校验值搬入内网；不要在 Windows 上直接下载 Linux ARM 包。正式生产更建议 Rocky Linux 9，但本项目仍提供 CentOS 7 模板。

## 离线介质

准备 JDK 17、Qdrant MUSL 二进制、本项目 JAR 和前端 dist。模型能力由现有内网模型网关提供，不搬运 Ollama 或本地模型；网关必须同时提供聊天接口和 Embedding 接口。Nginx 为可选项；没有 Nginx 时使用 `deploy/node/static-server.mjs` 和 `deploy/systemd/aibi-web.service`，不需要安装任何 npm 模块。Oracle 19c 由内网数据库团队提供 Service Name、账号、网络白名单和字符集信息；`ojdbc11` 已打包进应用 JAR，不要求在应用服务器安装 Oracle Client。每个搬运文件保存 SHA-256。

## 目录与启动

将后端 JAR 放到 `/opt/aibi/ai-bi-server.jar`，前端 dist 内容放到 `/opt/aibi/web`。根据 `deploy/aibi.internal-model.env.example` 创建 `/etc/aibi/aibi.env`，权限设为仅服务账号可读，并填写 Oracle 与内网模型网关地址。确认 Oracle和内网模型网关可达后，依次启动 Qdrant、AI BI，最后启动 Node 前端服务或企业批准的 Nginx。

检查：Qdrant `GET http://127.0.0.1:6333/healthz`，内网网关聊天与 Embedding 接口，应用 `GET http://127.0.0.1:8080/actuator/health`。

## 初次验证

先在开发机用默认 H2 + `AI_MODE=mock` 验证 SQL 安全与页面；再在隔离测试 Schema 使用 Oracle + `AI_MODE=mock` 验证 Oracle Flyway 迁移和示例查询；最后改成 `AI_MODE=real`，上传 `test-data/knowledge` 文档并执行黄金问题。平台元数据账号需要维护平台表，业务数据访问应拆分为独立只读账号或受控只读视图。当前 MVP 使用单数据源，接入真实业务库前应完成双数据源隔离。

## Oracle 19c 准备

建议为平台新建独立用户 `AIBI`，默认表空间配额按文档、审计和查询历史规模评估。首次启动需要 `CREATE SESSION`、`CREATE TABLE`、`CREATE SEQUENCE`、`CREATE TRIGGER`、`CREATE VIEW` 权限及表空间配额；Oracle 12c 以上身份列由数据库内部序列支撑。若由 DBA 预先执行迁移脚本，可在应用账号仅保留平台表的 DML 权限。中文测试数据建议数据库字符集使用 AL32UTF8。

迁移包同时包含平台表和虚构销售演示表，只能在独立空白测试 Schema 自动执行。DBA 手工执行并验收后，将环境变量 `FLYWAY_ENABLED=false`。严禁把带演示迁移的首次启动直接指向现有生产业务 Schema。
