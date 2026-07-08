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

1. 更新根目录 `build.gradle.kts` 中的 `version`，以及所有写死公开依赖版本的文档。
2. 更新 [`CHANGELOG.md`](CHANGELOG.md)，记录本次发布面向使用者的功能、行为、兼容性和迁移说明。
3. 将版本号和 changelog 更新合并到 `master`。
4. 推送匹配的 tag：

```bash
git tag v3.26.15
git push origin v3.26.15
```

发布 workflow 会校验 tag 等于 `v<project.version>`，然后执行
`./gradlew test` 和 `./gradlew publishReleaseToSonatype`。

本地可用以下命令 dry-run 校验 tag guard：

```bash
./gradlew verifyReleaseTagVersion -Prelease.tag=v3.26.15
```
