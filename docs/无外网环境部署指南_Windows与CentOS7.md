# 无外网环境部署指南（Windows 与 CentOS 7）

## 1. 适用范围与结论

本文适用于目标网络完全不能访问互联网，目标环境已经有 Oracle、JDK 17、Maven、Node.js 和 npm，但没有 Qdrant、没有 Nginx，并且不能确定 Maven 本地仓库和 npm 缓存是否完整的情况。

推荐采用“在当前已验证机器完成构建，目标机只运行成品”的方式：

- 后端运行 `release/ai-bi-server.jar`，这是包含运行依赖和 Oracle JDBC 驱动的 Spring Boot 可执行 JAR。
- 前端直接复制 `release/web`，不在目标机执行 `npm install` 或 `npm run build`。
- 使用 `deploy/node/static-server.mjs` 托管前端并转发 API。该脚本只使用 Node.js 内置模块，不需要任何 `node_modules`。
- Qdrant 必须在联网区提前下载与目标操作系统、CPU 架构匹配的离线安装包，再经过安全介质搬入。
- 完全无外网时不使用千问官网 API，也不部署 Ollama 或本地模型。聊天和 Embedding 都调用现有内网模型网关；网关需要提供 OpenAI-compatible `/chat/completions` 和 `/embeddings` 能力。

采用上述方案后，目标服务器上的 Maven/npm 依赖是否齐全不会影响运行。需要现场重新编译 Java 源码时，直接使用项目 `offline` 目录中的 Maven 3.9.11 与专用离线仓库；详细方法见 `docs/离线Maven仓库与二次开发说明.md`。

## 2. 当前项目已有与仍需准备的物料

### 2.1 项目内已经具备

| 物料 | 路径 | 用途 |
|---|---|---|
| 后端可执行 JAR | `release/ai-bi-server.jar` | JDK 17 直接运行，不需要 Maven |
| 前端生产文件 | `release/web` | 不需要 npm 或 node_modules |
| Windows Qdrant | `release/qdrant-x86_64-pc-windows-msvc-v1.19.0.zip` | Windows x64 使用 |
| Linux Qdrant MUSL | `release/qdrant-x86_64-unknown-linux-musl-v1.19.0.tar.gz` | CentOS 7 x86_64 首选 |
| Linux Qdrant GNU | `release/qdrant-x86_64-unknown-linux-gnu-v1.19.0.tar.gz` | 较新 glibc Linux 备用，不作为 CentOS 7 默认包 |
| Node 静态服务 | `deploy/node/static-server.mjs` | 托管 Vue、SPA 回退、转发后端 API |
| Oracle/Qdrant/AI 配置模板 | `deploy/aibi.env.example` | 后端环境变量模板 |
| Linux systemd 模板 | `deploy/systemd` | Qdrant、后端和前端服务；`ollama.service` 仅供其他部署方案选用 |
| 内网模型配置模板 | `deploy/aibi.internal-model.env.example` | 无 Ollama 时连接内网 Chat/Embedding 网关 |
| Qdrant 配置 | `deploy/qdrant` | Windows/Linux 数据目录和监听配置 |
| Qdrant 固定版本清单 | `deploy/qdrant/QDRANT_RELEASE_MANIFEST.txt` | 官方文件大小、SHA-256 和下载地址 |
| Linux Qdrant 下载脚本 | `scripts/download-qdrant-linux.ps1` | 支持断点续传并强制校验大小、哈希和压缩包结构 |
| 测试数据 | `test-data` | Foundry360 DDL、关系、指标、同义词、SQL 和文档 |
| 依赖清单 | `docs/packaged-jars.txt` | 后端 JAR 中实际包含的依赖 |
| 离线 Maven 开发包 | `offline` | Maven 3.9.11、完整本地仓库和 SHA-256 清单 |

### 2.2 搬迁前仍需补充到离线介质

1. 内网模型网关的 Chat/Embedding 基础地址、模型 ID、访问 Token、Embedding 维度、超时和网络放行信息。
2. 目标机实际可运行的 JDK 17 和 Node.js 版本信息。既然目标机已经安装，优先使用目标机现有版本，不要盲目替换。
3. Oracle 的主机、端口、Service Name/SID、平台 Schema 账号、业务只读账号和网络放行信息。
4. 所有搬运文件的 SHA-256 清单。

Qdrant 的 Windows、Linux MUSL 和 Linux GNU 三个 x86_64 包现已全部纳入 `release`。CentOS 7 的 glibc 较旧，应优先使用 MUSL 包，并在与目标机同版本的测试机上执行 `qdrant --version` 和实际写入测试。不要把 Windows 包、ARM 包或未经验证的 GNU 包部署到 CentOS 7。`scripts/download-qdrant-linux.ps1` 保留用于将来重新获取并校验 MUSL 包。

## 3. 为什么目标机不需要完整 Maven/npm 依赖

### 3.1 Maven

`ai-bi-server.jar` 是 Spring Boot 可执行 JAR，运行依赖已经位于 JAR 内部的 `BOOT-INF/lib`，包括 `ojdbc11`。目标机运行时只执行：

```text
java -jar ai-bi-server.jar --spring.profiles.active=real
```

因此 Oracle Client、Maven、本地 `.m2` 仓库都不是运行条件。

项目已经自带 Maven 3.9.11 和验证过的专用仓库。目标机直接执行：

```text
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\mvn-offline.ps1 clean test package
```

CentOS 7 执行 `scripts/mvn-offline.sh clean test package`。仓库已经通过严格 `--offline` 构建；以后新增依赖时仍必须在联网构建机更新仓库并重新完成离线验证。

### 3.2 npm

`release/web` 已经是生产构建结果。Node.js 只负责读取这些静态文件，`deploy/node/static-server.mjs` 没有第三方依赖，因此不需要 `npm install`、`serve`、`http-server` 或 `node_modules`。

如果必须在内网修改并重新构建前端，最可靠方式是在联网构建机使用锁文件完成一次 `npm ci` 和 `npm run build`，再搬运构建结果。搬运 npm 缓存后运行 `npm ci --offline` 仍可能因缓存缺包失败，不能作为首选部署方案。

## 4. Node.js 能否代替 Nginx

可以。项目提供的 `static-server.mjs` 同时具备：

- Vue 单页应用路由回退到 `index.html`；
- `/api/*` 和 `/actuator/*` 转发到 Spring Boot；
- Excel、CSV、XML 下载响应透传；
- 不依赖 npm 模块；
- Windows 和 Linux 使用同一份脚本。

不建议在无网服务器直接执行 `npx serve` 或 `npx http-server`，因为本地没有对应包时，`npx` 会尝试联网下载。普通 `http-server` 还需要额外配置 SPA 回退和 API 代理。

Node 方案适合内网单机和中低并发部署。若以后需要 HTTPS、统一证书、访问日志轮转、限流、多实例负载均衡或安全设备联动，再换成企业批准的 Nginx/Apache/网关；Spring Boot 和前端文件不需要改变。

## 5. 通用部署前检查

在目标机记录并核验：

```text
java -version
node -v
npm -v
mvn -v
```

JDK 必须为 17。Node.js 建议为仍受维护且能在目标操作系统实际运行的版本，至少应支持 ES Module；不要只看安装目录，要实际执行 `node -v` 和 `node deploy/node/static-server.mjs` 验证。

Oracle 连接建议使用 Service Name：

```text
jdbc:oracle:thin:@//数据库主机:1521/服务名
```

平台首次启动会运行 Oracle Flyway 迁移。只能指向独立测试或平台 Schema，不能把带演示表的首次启动直接指向既有生产业务 Schema。生产环境建议由 DBA 审核并预执行迁移，之后设置 `FLYWAY_ENABLED=false`。

## 6. Windows 无外网部署

### 6.1 推荐目录

```text
D:\AIBI\app\ai-bi-server.jar
D:\AIBI\app\web\...
D:\AIBI\app\static-server.mjs
D:\AIBI\config\aibi.env.ps1
D:\AIBI\qdrant\qdrant.exe
D:\AIBI\qdrant\config.yaml
D:\AIBIData\qdrant\storage
D:\AIBIData\qdrant\snapshots
D:\AIBIData\storage
```

将 `release/web` 的内容复制到 `D:\AIBI\app\web`，将 `deploy/node/static-server.mjs` 复制到 `D:\AIBI\app\static-server.mjs`。解压项目已有的 Windows Qdrant 包，并根据实际盘符修改 `deploy/qdrant/config.windows.yaml`。

### 6.2 环境变量

启动 Java 前至少配置：

```powershell
$env:SPRING_PROFILES_ACTIVE = 'real'
$env:DB_URL = 'jdbc:oracle:thin:@//oracle-host:1521/service-name'
$env:DB_USERNAME = 'aibi'
$env:DB_PASSWORD = '请从安全配置读取'
$env:FLYWAY_ENABLED = 'true'
$env:AI_MODE = 'real'
$env:AI_CHAT_PROVIDER = 'qwen-api'
$env:AI_EMBEDDING_PROVIDER = 'qwen-api'
$env:QWEN_API_BASE_URL = 'http://internal-model-gateway.example.local/v1'
$env:DASHSCOPE_API_KEY = '请填写内网网关Token'
$env:QWEN_API_CHAT_MODEL = '内网聊天模型ID'
$env:QWEN_API_EMBEDDING_MODEL = '内网向量模型ID'
$env:QWEN_API_EMBEDDING_DIMENSIONS = '1024'
$env:QDRANT_BASE_URL = 'http://127.0.0.1:6333'
$env:QDRANT_COLLECTION_PREFIX = 'aibi'
$env:QDRANT_VECTOR_SIZE = '1024'
$env:STORAGE_ROOT = 'D:\AIBIData\storage'
```

密码不要写入代码仓库或普通批处理文件。使用仅服务账号可读的配置文件、Windows Credential Manager 或企业密钥系统。

### 6.3 启动顺序

1. 确认 Oracle 端口和账号可用。
2. 启动 Qdrant，并检查 `http://127.0.0.1:6333/`。
3. 调用内网网关验证 Chat 和 Embedding 接口，并记录 Embedding 实际维度。
4. 启动后端：`java -Xms1g -Xmx4g -jar D:\AIBI\app\ai-bi-server.jar`。
5. 配置并启动前端：

```powershell
$env:WEB_ROOT = 'D:\AIBI\app\web'
$env:WEB_HOST = '0.0.0.0'
$env:PORT = '5173'
$env:BACKEND_URL = 'http://127.0.0.1:8080'
node D:\AIBI\app\static-server.mjs
```

正式长期运行可使用企业允许的 Windows 服务包装器或计划任务，并为 Java、Node、Qdrant 分别设置服务账号、自动重启和日志目录。

## 7. CentOS 7 无外网部署

### 7.1 风险前置

CentOS 7 已停止维护，生产部署应先完成安全例外审批。执行 `uname -m` 确认为 `x86_64`，执行 `ldd --version` 记录 glibc；Qdrant 优先采用同版本 MUSL 发布物。若 MUSL 包也无法在代表性机器运行，不要升级系统 glibc，改用企业允许的容器方案、升级操作系统，或在相同基线环境重新构建并完整测试。

### 7.2 目录和账号

```text
/opt/aibi/ai-bi-server.jar
/opt/aibi/web/...
/opt/aibi/static-server.mjs
/opt/aibi/data/storage
/opt/qdrant/qdrant
/opt/qdrant/config.yaml
/opt/qdrant/storage
/opt/qdrant/snapshots
/etc/aibi/aibi.env
/etc/aibi/aibi-web.env
```

创建 `aibi`、`qdrant` 两个不可登录服务账号，分别赋予对应目录最小权限。`/etc/aibi/aibi.env` 权限设为 `600`，不要让前端服务读取包含 Oracle 密码和模型网关 Token 的后端环境文件。

### 7.3 安装部署文件

- 复制 `release/ai-bi-server.jar` 到 `/opt/aibi/`。
- 复制 `release/web` 内容到 `/opt/aibi/web/`。
- 复制 `deploy/node/static-server.mjs` 到 `/opt/aibi/`。
- 复制 `deploy/qdrant/config.yaml` 到 `/opt/qdrant/`。
- 复制 `deploy/systemd/*.service` 到 `/etc/systemd/system/`。
- 按 `deploy/aibi.internal-model.env.example` 创建 `/etc/aibi/aibi.env`。
- 按 `deploy/aibi-web.env.example` 创建 `/etc/aibi/aibi-web.env`。

如果 Node 可执行文件不是 `/usr/bin/node`，修改 `aibi-web.service` 的 `ExecStart`。如果 Java 不在 `/usr/bin/java`，同样修改 `aibi.service`。

### 7.4 启动

```text
systemctl daemon-reload
systemctl enable --now qdrant
systemctl enable --now aibi
systemctl enable --now aibi-web
```

检查日志：

```text
journalctl -u qdrant -n 100 --no-pager
journalctl -u aibi -n 100 --no-pager
journalctl -u aibi-web -n 100 --no-pager
```

如果前端直接供内网用户访问，只放行 TCP 5173；8080、6333、6334、11434 应默认仅监听本机或通过主机防火墙限制。若启用 SELinux，需要由管理员为自定义目录、端口和服务建立正式策略，不要把永久关闭 SELinux 当作解决方案。

## 8. Qdrant 首次安装与迁移

目标环境没有 Qdrant 时有两种方式：

1. 新建空 Qdrant：启动后登录 AI 训练中心，导入/发布 DDL、文档、指标、关系、同义词和标准 SQL，系统重新生成向量。
2. 搬运已有索引：在源端停止写入并创建 Qdrant snapshot，目标端使用相同 Qdrant 主版本、相同 embedding 模型与相同向量维度恢复。

示例配置使用 1024 维，但最终必须以内网 Embedding 接口实际返回维度为准，并让 `QWEN_API_EMBEDDING_DIMENSIONS` 与 `QDRANT_VECTOR_SIZE` 完全一致。更换模型、provider 或维度时必须建立新集合并重新索引，不能把不同向量混入同一集合。

Qdrant 数据目录、Oracle 数据和上传文档必须纳入备份。只复制 Qdrant 的运行程序不等于复制知识库。

## 9. 验收顺序

1. `GET http://127.0.0.1:6333/` 返回 Qdrant 版本信息。
2. 内网网关 Chat 与 Embedding 测试都成功，Embedding 返回维度与配置一致。
3. `GET http://127.0.0.1:8080/actuator/health` 返回 `UP`。
4. 浏览器访问 `http://服务器地址:5173/`，登录并刷新子路由不出现 404。
5. AI 训练中心的平台信息显示 `real / qdrant / qwen-api`；这里的 `qwen-api` 是当前 OpenAI-compatible 兼容层标识，实际地址指向内网网关。
6. 上传一份知识文档并确认 Qdrant 点数增加。
7. 执行一个命中标准 SQL的问题和一个由模型生成 SQL 的问题。
8. 验证 Oracle 查询、权限隔离、查询反馈以及 Excel/CSV/XML 下载。
9. 执行 `test-data/foundry360` 的黄金问题并保存验收结果。

## 10. 搬迁包最终清单

| 类别 | Windows | CentOS 7 |
|---|---|---|
| 应用 | JAR、web、Node 脚本 | JAR、web、Node 脚本 |
| Java | 已安装 JDK 17 或离线包 | 已安装 JDK 17 或兼容 RPM/压缩包 |
| Node | 已安装且实际可运行 | 已安装且实际可运行 |
| Qdrant | Windows x64 1.19.0 包 | Linux x86_64 MUSL 1.19.0 包 |
| 模型服务 | 内网 Chat/Embedding 网关配置 | 内网 Chat/Embedding 网关配置 |
| 数据库 | Oracle 连接与账号 | Oracle 连接与账号 |
| 配置 | PowerShell 安全配置 | env 文件和 systemd 单元 |
| 测试 | test-data、验收问题 | test-data、验收问题 |
| 完整性 | SHA-256 清单 | SHA-256 清单 |

迁移前应在一台真正断网的同操作系统测试机完整演练一次。验收标准是目标机只访问获批的 Oracle 与内网模型网关，不访问 Maven Central、npm registry、GitHub、Ollama registry 或千问公网 API，也能完成启动、知识导入、向量检索、Oracle 查询和报告下载。
