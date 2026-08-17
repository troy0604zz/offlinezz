# 离线 Maven 仓库与二次开发说明

## 1. 结论

项目不再只提供可运行 JAR。`offline` 目录已经包含 Maven 3.9.11 和本项目专用的 Maven 本地仓库，可在没有外网、没有 Maven Central、没有阿里云 Maven 镜像的环境中继续修改 Java 源码、编译、运行 11 个测试并生成 Spring Boot 可执行 JAR。

离线仓库不是简单复制 `release/ai-bi-server.jar` 中的运行依赖，还包含：

- 编译期和运行期依赖；
- H2/Oracle 驱动及 Flyway Oracle 模块；
- 测试依赖和 Surefire/JUnit Platform；
- Maven Clean、Resources、Compiler、Jar 等生命周期插件；
- Spring Boot Maven Plugin 及其插件依赖；
- Tika 文档解析器的完整传递依赖。

## 2. 已完成验证

生成仓库后，系统在一个不含 `target`、不使用用户默认 `.m2/repository` 的临时源码目录中执行了两次构建：

1. 使用专用仓库在线构建，补齐 `dependency:go-offline` 可能遗漏的插件文件。
2. 使用同一仓库加 Maven `--offline` 强制断网模式执行 `clean test package`。

最终结果：58 个主源码文件和 4 个测试源码文件编译成功；11 个测试全部通过；Spring Boot repackage 成功；严格离线构建状态为 `PASS`。

## 3. Windows 命令行使用

确认 JDK 17：

```text
java -version
```

在项目根目录执行默认完整构建：

```text
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\mvn-offline.ps1
```

如果后端正在运行，Windows 会锁定 `server/target` 中的 JAR，`clean` 无法删除它。进行二次开发构建前先停止后端进程；这属于文件锁，不是离线仓库缺包。

也可以传入普通 Maven 目标：

```text
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\mvn-offline.ps1 test
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\mvn-offline.ps1 package -DskipTests
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\mvn-offline.ps1 spring-boot:run
```

脚本固定使用：

- Maven：`offline/apache-maven-3.9.11/bin/mvn.cmd`
- 本地仓库：`offline/maven-repository`
- 专用设置：`offline/maven-settings.xml`
- 离线开关：`--offline`
- 项目目录：`server`

专用设置保留了生成离线包时的 Maven 镜像标识，使缓存构件能被 Maven 正确识别。虽然配置中存在镜像 URL，但脚本始终强制 `--offline`，因此不会访问公网仓库。

## 4. CentOS 7 命令行使用

首次复制后赋予脚本执行权限：

```text
chmod +x scripts/mvn-offline.sh
chmod +x offline/apache-maven-3.9.11/bin/mvn
```

确认 `JAVA_HOME` 指向 JDK 17，然后执行：

```text
scripts/mvn-offline.sh clean test package
```

生成的 JAR 位于 `server/target/ai-bi-server-0.1.0-SNAPSHOT.jar`。

## 5. IDEA 配置

打开 `server/pom.xml` 所在项目后，在 IDEA 设置中配置：

1. Project SDK 和 Maven Runner JRE 都选择 JDK 17。
2. Maven home path 选择项目内的 `offline/apache-maven-3.9.11`。
3. Local repository 选择项目内的 `offline/maven-repository`。
4. User settings file 选择项目内的 `offline/maven-settings.xml`。
5. 勾选 Maven 的 Work offline。
6. 重新加载 Maven 项目。

不要把 IDEA 指向空的内网默认 `.m2/repository`，否则即使 Maven 本体存在，依赖仍会显示无法解析。

## 6. 完整性校验

项目初次搬到内网后执行：

```text
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\verify-offline-maven-repo.ps1
```

校验通过后再执行离线构建。Linux 可使用企业已有的 `sha256sum -c` 工具按 `offline/OFFLINE_SHA256SUMS.txt` 检查；清单中的路径相对于 `offline` 目录。

## 7. 二次开发新增依赖

现有仓库只保证当前 `pom.xml` 的完整构建。如果以后在 `pom.xml` 新增依赖或 Maven 插件，必须在可联网且经过审批的构建机更新离线仓库，并再次执行严格离线构建。不能只复制一个新增 JAR，因为它可能还有 POM、父 POM、BOM 和传递依赖。

推荐更新流程：

```text
mvn -Dmaven.repo.local=offline/maven-repository dependency:go-offline
mvn -Dmaven.repo.local=offline/maven-repository clean test package
mvn -o -Dmaven.repo.local=offline/maven-repository clean test package
```

更新完成后重新生成 `OFFLINE_SHA256SUMS.txt`，记录依赖变更原因和审批信息。大型二进制仓库不建议直接提交到普通 Git 仓库，可使用企业制品库或作为经过校验的离线介质管理。
