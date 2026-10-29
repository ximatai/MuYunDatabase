# 发布流程

推送发布 tag 后，GitHub Actions 会自动发布到 Maven Central。

## 必需仓库密钥

推送发布 tag 前，需要先配置以下 repository secrets：

- `SONATYPE_TOKEN`: Sonatype Central user token name.
- `SONATYPE_PASSWORD`: Sonatype Central user token password.
- `SIGNING_KEY_ID`: PGP 签名 key id。
- `SIGNING_PASSWORD`: PGP 签名 key passphrase。
- `SIGNING_SECRET_KEY_BASE64`: Base64 编码后的 ASCII-armored PGP 私钥。

也支持使用 `SIGNING_SECRET_KEY` 直接保存 ASCII-armored 私钥，但
`SIGNING_SECRET_KEY_BASE64` 可以避免 CI secret 中的换行处理问题。

可用以下命令从本地签名 key 生成 base64 secret：

```bash
gpg --armor --export-secret-keys <KEY_ID> | base64 | tr -d '\n'
```

## 发布版本

1. 更新根目录 `build.gradle.kts` 中的 `version`、Quarkus extension descriptor 的 deployment-artifact 版本，以及文档与 samples 中写死的依赖版本。
2. 更新 [`CHANGELOG.md`](CHANGELOG.md)，记录本次发布面向使用者的功能、行为、兼容性和迁移说明。
3. 通过 [`ROADMAP.md`](ROADMAP.md#发布门禁) 的发布门禁，包括强制 PostgreSQL JVM 矩阵、Quarkus H2/PostgreSQL native smoke 和白名单本地发布验证。native 使用当前 Quarkus 支持的 GraalVM 工具链，不能以旧工具链构建失败或跳过测试代替验收；具体命令见 [`QUARKUS.md`](QUARKUS.md#测试矩阵)。
4. 将版本号和 changelog 更新合并到 `master`，确认 CI 通过。
5. 推送匹配的 tag：

```bash
releaseVersion=<new-version>
git tag "v${releaseVersion}"
git push origin "v${releaseVersion}"
```

发布 workflow 会校验 tag 等于 `v<project.version>`，然后执行
发布工具测试、`./gradlew test`、无签名产物盘点与 Spring Boot/Quarkus 消费者构建，最后执行
`./gradlew publishReleaseToSonatype -Pmuyun.mavenCentralRelease=true`。

本地可用以下命令 dry-run 校验 tag guard：

```bash
releaseVersion=<new-version>
./gradlew verifyReleaseTagVersion -Prelease.tag="v${releaseVersion}"
```

## 统一 Maven 发布

六个公开模块仍保留独立坐标与统一版本。根任务将其当前版本目录汇集为一个 bundle，
每版只上传一次 deployment；不会合并 JAR，也不减少源码、Javadoc、签名或 Gradle 元数据。
Quarkus runtime 与 deployment 构件必须同时存在且版本一致。测试模块与 samples 不发布。

本地验证不需要签名或 Sonatype 凭据：

```bash
./gradlew reportMavenPublication verifyPublishedConsumers
```

`publishReleaseToConsumerRepository` 将六个模块写入 `build/consumer-repo`；
`publishReleaseToLocalRepository` 保留为该无签名入口的别名。
`verifyPublishedConsumers` 让现有 Spring Boot 与 Quarkus samples 从该仓库解析平台坐标，
禁用 composite build / project dependency，并阻止这些坐标回退到 Maven Central；执行 JVM 构建，
包含 Quarkus augmentation，但不替代数据库运行测试和 native 发布门禁。
`reportMavenPublication` 输出文件数、未压缩体积和模块清单到 `build/maven-publication-report.json`。

发布工具需要 Node.js 22 或更高版本，仅使用内置库，无 npm 安装步骤；普通 Java 构建不需要 Node。
本地无签名盘点不代表签名后的发布用量。统一 bundle 主要减少部署次数，构件数量与体积基本不变。

签名 staging 与上传均要求显式发布模式以及匹配的 tag。签名凭据只在正式模式加载，
Sonatype 凭据只用于上传与状态恢复；Release CI 不向测试和消费者构建注入发布凭据。
如需生成不上传的正式结构预览，提供签名凭据后执行：

```bash
./gradlew bundleMavenCentralRelease -Pmuyun.mavenCentralRelease=true -Prelease.tag=v<version>
```

产物为 `build/maven-central/MuYunDatabase-<version>.zip`。校验器拒绝缺失附件、校验和不符、
多余文件、符号链接、历史版本及仓库级 metadata；签名报告保存为 `build/maven-central/report.json`。
清理在验证之前单独执行，上传任务不再混入 clean。不要在并行 Gradle 调用中同时清理或写入 staging。

上传后只跟踪同一 deployment，状态达到 `PUBLISHED` 才成功。状态请求临时失败可重试，
发布请求回执不确定时先查询状态，不盲目重复发布。等待上限 15 分钟；失败后先读取 CI 留存的
`build/sonayUploader/uploaderId` 和报告，不重新执行上传。使用同一凭据恢复已有部署：

```bash
node scripts/wait-maven-central.mjs <deployment-id-file>
# 仅当原部署停在 VALIDATED 且需要继续发布时：
node scripts/wait-maven-central.mjs <deployment-id-file> --publish
```

这两个命令不创建 deployment、不重新上传产物；已在 PUBLISHING/PUBLISHED 的部署不会再次提交发布。
`publishToSonatype` 兼容入口委派给统一发布任务，不再使用插件原有的独立轮询。
