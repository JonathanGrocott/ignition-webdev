# Release Checklist

Use this checklist before creating a public release.

## 1. Preflight

- [ ] Confirm working tree is clean: `git status --short`
- [ ] Confirm target branch is up to date: `git pull --ff-only`
- [ ] Confirm module version in `gradle.properties` is set correctly (`moduleVersion=...`)
- [ ] Verify that `/system/webdev/*` is available on the target Gateway. If Event Streams is installed and enabled, also verify that `/system/eventstream/*` is available.

## 2. Quality Gates

- [ ] Run full build and tests: `./gradlew clean build`
- [ ] Run the hosted-file smoke test against a test Gateway:
  - `./scripts/parity_phase2_live.sh http://localhost:8088 webcompat_live_parity`
- [ ] (Optional) Run targeted smoke test:
  - `./scripts/smoke_webdev.sh http://localhost:8088 webcompat_live_parity text GET 200`

## 3. Signing

- [ ] Build release artifact:
  - `./gradlew clean build`
  - `./scripts/sign-module.sh`
- [ ] Verify signed artifact:
  - `unzip -l webcompat-build/build/webdev-compat.modl | grep -E 'certificates.p7b|signatures.properties'`

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
