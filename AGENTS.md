# AGENTS.md

Guidelines for AI agents and contributors working on this repository.

## Project overview

Fruit Slice is a small Android game: pure Kotlin, custom `View` rendering,
zero third-party dependencies, no network access. Build with Gradle 8.x /
AGP 8.5.2 / JDK 17 (JDK 11+ required).

- Game loop and rendering: `app/src/main/java/com/alair/fruitslice/GameView.kt`
- Activities: `MainActivity.kt`, `SplashActivity.kt`
- Sound-effect generator: `scripts/gen_sounds.py` (pure Python stdlib)

## Hard requirements

### 1. Never commit privacy or personal information

This repository is public. Before every commit and push, verify that NO
tracked file contains any of the following:

- Local filesystem paths: `/home/`, `/mnt/`, `/opt/`, `C:\`, `sdk.dir`, or
  any machine-specific absolute path
- Usernames, aliases, or handles: the account's private aliases
  (patterns: `fdezh[e]ng`, `f[d]z`, `dx[d]ragon`, `dezh[e]ngfeng`) or
  similar internal identifiers
- Personal email addresses (only the GitHub noreply address is allowed:
  `DearFamilyYuan@users.noreply.github.com`)
- Internal hostnames, company names, server IPs, share names
- Credentials of any kind: passwords, tokens, API keys, keystores

Check with (run from repo root; AGENTS.md is excluded because it documents
these patterns itself):

```bash
git grep -niE 'fdezh[e]ng|f[d]z|dx[d]ragon|dezh[e]ngfeng|/home/|/mnt/|sdk\.dir' -- . \
  ':!AGENTS.md' && echo 'FAIL: privacy string found' || echo 'privacy check clean'
```

Also check the commit itself: `git log -1 --format='%an %ae %cn %ce'` must
show only the GitHub noreply identity. If a secret ever lands in history,
stop and tell the user immediately — rewriting history is not enough once
pushed; the credential must be rotated.

### 2. Signing material stays local

- `keystore/`, `*.keystore`, `keystore.properties` are git-ignored and must
  never be added, renamed around the ignore rules, or force-added
- Only `keystore.properties.example` (placeholder values) belongs in git
- Release signing is optional: without `keystore.properties` the release
  build is unsigned by design — do not "fix" this by hardcoding passwords
  into `gradle.properties` or `app/build.gradle`

### 3. Keep it dependency-free

Do not add third-party libraries, new Gradle plugins, or the INTERNET
permission without explicit user approval. The value of this project is
that it builds from AOSP APIs alone.

### 4. Build hygiene

- `.gitignore` must keep excluding: `build/`, `.gradle/`, `local.properties`,
  `.idea/`, `*.apk`, `*.aab`
- Verify before pushing:

  ```bash
  JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 gradle assembleDebug
  ```

  (This machine's default `JAVA_HOME` points at Java 8; override it.)

## Workflow

1. Make changes; keep commits focused and messages in English.
2. Run the privacy check (Hard requirement 1) and a debug build.
3. Push only to `main` of `https://github.com/DearFamilyYuan/FruitSlice`.
4. If anything sensitive is detected in a commit or on the remote, halt and
   report to the user before proceeding.

## License

MIT — see `LICENSE`.
