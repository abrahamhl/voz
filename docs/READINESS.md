# Release readiness matrix

Status is evidence-based. `OPEN` means the work or proof is still required; it
does not mean the product is broken.

| Gate | Status | Evidence / next action | Owner |
|---|---|---|---|
| Canonical code is on the reviewable stack | DONE | PRs #5-#8, stacked from the P7-P11 lineage | Maintainer |
| JVM tests | DONE | 339 tests were green before the PR D source-set split; rerun in CI on PR #8 | CI |
| Debug and release Kotlin compilation | DONE | `compileDebugKotlin` and `compileReleaseKotlin` pass on PR #8 | CI |
| Release has no cloud planner or OkHttp | DONE | Cloud files are `src/debug`; release has a null factory; `releaseRuntimeClasspath` has no `okhttp3` | CI |
| Release APK has no `INTERNET` and cloud bool is false | OPEN | Existing CI assertion; rerun after PR #8 merges | CI |
| Release AAB builds and passes the same assertions | OPEN | Tag workflow runs `bundleRelease` and `verify-release.sh` | CI |
| Release signing | OPEN | Add `VOZ_KEYSTORE_BASE64`, `VOZ_KEYSTORE_PASSWORD`, `VOZ_KEY_ALIAS`, `VOZ_KEY_PASSWORD` | Maintainer |
| Physical device smoke run | OPEN | Run `scripts/device-smoke.sh <apk>`; attach evidence folder to device issue | Tester |
| Two-phone validation | OPEN | One Android 13+, one TalkBack run; complete sections A-E | Tester |
| Accessibility / red-team acceptance | OPEN | Record every pass and failure in `docs/evidence/TEMPLATE.md` | Tester |
| Play accessibility declaration | OPEN | Recheck policy, record demo video, submit declaration | Maintainer |
| Privacy policy URL | OPEN | Publish `docs/PRIVACY.md` at a stable HTTPS URL and link it in Play | Maintainer |
| Data Safety form | OPEN | Submit only after release artifact and privacy URL are final | Maintainer |
| Business validation | OPEN | Recruit Dutch/Spanish accessibility partners; do not claim retention or revenue yet | Founder |

## Go / no-go

Do not call the build a public release until every `OPEN` item that is a store or
user-safety gate has evidence attached. In particular:

- A green CI run is not a substitute for a physical-device run.
- An unsigned APK/AAB is not a Play upload.
- A draft Play declaration is not an approval.
- A pitch hypothesis is not user or revenue evidence.

## Minimum release packet

Keep these together for each candidate tag:

1. Tag, commit and `versionName`.
2. Signed APK/AAB and `SHA256SUMS.txt`.
3. CI run URL and test summary.
4. Device evidence for the exact APK hash.
5. Play declaration, privacy URL and Data Safety answers.
6. Known limitations and failures, not only successful screenshots.
