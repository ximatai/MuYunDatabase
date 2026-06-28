# Release

Releases are published from GitHub Actions when a release tag is pushed.

## Required Repository Secrets

Configure these repository secrets before pushing a release tag:

- `SONATYPE_TOKEN`: Sonatype Central user token name.
- `SONATYPE_PASSWORD`: Sonatype Central user token password.
- `SIGNING_KEY_ID`: PGP signing key id.
- `SIGNING_PASSWORD`: PGP signing key passphrase.
- `SIGNING_SECRET_KEY_BASE64`: Base64-encoded ASCII-armored PGP private key.

`SIGNING_SECRET_KEY` is also supported for a plain ASCII-armored private key, but
`SIGNING_SECRET_KEY_BASE64` avoids newline handling issues in CI secret values.

Create the base64 secret from a local signing key with:

```bash
gpg --armor --export-secret-keys <KEY_ID> | base64 | tr -d '\n'
```

## Publish a Release

1. Update `version` in the root `build.gradle.kts` and any documentation that
   embeds the public dependency version.
2. Merge the version bump to `master`.
3. Push a matching tag:

```bash
git tag v3.26.12
git push origin v3.26.12
```

The release workflow validates that the tag equals `v<project.version>`, runs
`./gradlew test`, then runs `./gradlew publishReleaseToSonatype`.

For a local dry check of the tag guard:

```bash
./gradlew verifyReleaseTagVersion -Prelease.tag=v3.26.12
```
