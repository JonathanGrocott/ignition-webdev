# Release Checklist

Use this checklist before creating a public release.

## 1. Preflight

- [ ] Confirm working tree is clean: `git status --short`
- [ ] Confirm target branch is up to date: `git pull --ff-only`
- [ ] Confirm module version in `gradle.properties` is set correctly (`moduleVersion=...`)
- [ ] Confirm built-in Ignition Web Dev module is disabled/uninstalled on production targets (path ownership conflict with `/system/webdev/*`)

## 2. Quality Gates

- [ ] Run full build and tests: `./gradlew clean build`
- [ ] Run live parity suite against a real gateway:
  - `./scripts/parity_phase2_live.sh http://localhost:8088 webcompat_live_parity`
- [ ] (Optional) Run targeted smoke test:
  - `./scripts/smoke_webdev.sh http://localhost:8088 webcompat_live_parity text GET 200`

## 3. Signing Decision

- [ ] Decide artifact type for this release:
  - Unsigned (default): keep `skipModlSigning=true`
  - Signed: run with `-PskipModlSigning=false` plus required signing configuration
- [ ] Build release artifact:
  - Unsigned: `./gradlew clean build`
  - Signed: `./gradlew clean build -PskipModlSigning=false`

## 4. Tag and Publish

GitHub release workflow triggers on tags matching `v*`.

- [ ] Commit release changes:
  - `git add -A`
  - `git commit -m "release: vX.Y.Z"`
- [ ] Create annotated tag matching `moduleVersion`:
  - `git tag -a vX.Y.Z -m "Release vX.Y.Z"`
- [ ] Push branch and tag:
  - `git push origin main`
  - `git push origin vX.Y.Z`
- [ ] Verify GitHub Actions `Build and Release` succeeded and attached the `.modl` artifact

## 5. Post-Release

- [ ] Install module in staging/production gateway and verify representative endpoints
- [ ] Record release notes/changelog entry
- [ ] If signed release was required, verify consumers received signed artifact
