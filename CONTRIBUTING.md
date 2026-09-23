# Contributing

Thanks for helping make phones usable by voice. Please read the [code of conduct](CODE_OF_CONDUCT.md). If you have an Android phone, the most useful contribution right now is running the [device validation pack](docs/DEVICE_VALIDATION.md) and reporting the results.

## Ground rules

- **Accessibility first.** Every UI change must work with TalkBack, keep 48 dp+ touch targets and have strings in `values`, `values-es` and `values-nl`.
- **Nothing that restores basic access goes behind a paywall** (see [Business model](docs/BUSINESS_MODEL.md)).
- **Safety is not optional.** New actions go through the fixed vocabulary in `:core` (`Verb`), the validator and, if they can cost money or send/delete something, the confirmation flow.
- **Privacy:** no analytics, no tracking SDKs, no new network calls without an opt-in.

## Development

- `:core` is pure Kotlin: put logic there and cover it with table-driven tests (ES/EN/NL cases, plus adversarial cases).
- `:app` holds Android glue only.
- Run before opening a pull request:

```bash
./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
bash scripts/secret-scan.sh
```

## Adding a language

1. Add the language to `Lang` and a grammar object in `core/.../parse/Grammars.kt`.
2. Add parser test cases for every command.
3. Add `values-xx/strings.xml` and sensitive-target / kill-phrase / confirmation words in `safety/Safety.kt`.

## Commits and pull requests

Conventional Commits (`feat:`, `fix:`, `docs:`, `ci:`…). One logical change per pull request, with tests. By contributing you agree your work is licensed under the [Apache License 2.0](LICENSE).
