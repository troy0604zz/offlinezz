# AI BI 后端离线 Maven 开发包

本目录用于在完全无外网环境中继续开发、测试和打包 `server` 后端。

- `apache-maven-3.9.11`：项目已经验证通过的 Maven 本体，Windows/Linux 通用二进制发行版。
- `maven-repository`：项目专用 Maven 本地仓库，包含编译、测试、Spring Boot 打包和相关插件依赖。
- `maven-settings.xml`：保持离线仓库来源标识一致的专用配置；离线构建不会访问其中的网址。
- `OFFLINE_SHA256SUMS.txt`：本目录文件的初始 SHA-256 清单，不包含清单文件自身。

Windows 在项目根目录执行：

```text
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\mvn-offline.ps1 clean test package
```

CentOS 7 在项目根目录执行：

```text
chmod +x scripts/mvn-offline.sh offline/apache-maven-3.9.11/bin/mvn
scripts/mvn-offline.sh clean test package
```

完整的 IDEA 配置、验证结果和依赖更新方式见 `docs/离线Maven仓库与二次开发说明.md`。
