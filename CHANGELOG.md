# Changelog

All notable changes to this project are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses [Semantic Versioning](https://semver.org/).

## [Unreleased]

Release-candidate work targeting `0.2.0-rc1` (the `versionName` in `app/build.gradle.kts`). Still not validated on a physical phone.

### Security
- A cloud tap on a label the user never said, or a cloud search for text the user never said, needs a spoken or on-screen "yes".
- Wider sensitive-action list in ES/EN/NL (subscribe, join, rent, allow, install, accept, confirm, share, post, empty trash) plus prices.
- Cloud replies with digits, links, e-mail addresses, sensitive verbs or instruction-like text are not spoken; the rest is prefixed "Gemini says".
- After "yes", the target is re-checked on the fresh screen; identical buttons (one "Delete" per row) are never guessed; the check includes the texts inside the pressed container.
- With several text fields and none focused, VOZ asks instead of typing into the first one.
- A question that could not be spoken counts as "no".
- Personal data (e-mail, IBAN, codes, phone and card numbers) is masked before the cloud planner sees the screen; the app name in front is no longer sent; on-phone brains are asked first.
- Backups and device-to-device transfer of app data are excluded.

### Changed
- The Gemini cloud brain is available only in developer and pilot builds, behind a consent screen that states Google's Gemini API terms (professional use, adults, paid keys in the EEA/CH/UK). Release builds exclude its planner and HTTP client, and drop the internet permission.
- New accessible interface: icon-only mic with real listening state, last-command card, on-screen Yes/No for confirmations, one banner per problem with one fix, three-step setup with the restricted-settings steps in the right order, grouped settings with steppers, readable history, About and privacy screen.
- The floating mic shows its state with colour, icon and TalkBack state, can be moved with accessibility actions, tolerates tremor and stays on screen.
- Text-to-speech recovers from a dead engine; failures play an error tone and show a banner.
- After a tap or back, the next step waits for the app to react instead of a fixed delay; forced landscape restores the user's auto-rotate.

### Removed
- The Gemini key step from onboarding and the "comment scrolls" slider.

## [0.1.0-demo] - 2026-09-23

First demo build. Debug-signed APK for sideloading; not published on Google Play.

### Added
- Offline command grammar in Spanish, English and Dutch: open apps (fuzzy, with spoken aliases), search on YouTube/Google/Maps/Play, back, home, recents, notifications, quick settings, scroll, tap by label, type, read the screen, volume, rotate, stop.
- App intent routing: say an app name to open it, or an app name plus words to search inside it.
- YouTube flows: enter/exit full screen (label search in ES/EN/NL, rotate fallback) and read the most popular, funniest or topic-matching comments after scrolling the comments panel.
- Decision engines: offline local engine (default), optional bring-your-own-key Google Gemini cloud planner with a JSON schema limited to the fixed action vocabulary, and a disabled Jev engine stub.
- Safety: spoken confirmation before sensitive taps (send, pay, buy, delete, call, transfer), kill phrases, plan validator, screen text fenced as untrusted data, cloud plans can only type dictated words, on-device action log.
- Accessibility service with gestures and accessibility-button trigger; floating mic foreground service; in-app big mic button; earcons; offline-first speech recognition with online retry; TTS with audio focus.
- Onboarding in 4 steps (microphone, floating mic, accessibility service with Android 13+ restricted-settings guide, optional cloud key); settings (language, cloud, comment depth, speech rate, bubble size, high contrast); action log screen.
- Android Keystore–encrypted storage for the API key; DataStore settings.
- ES/EN/NL user interface.
- CI: secret scan, 266 unit tests, Android lint and debug APK on every push; release workflow attaching `voz-demo.apk` to tagged releases.
- Documentation: README, pitch, business model, architecture, privacy, security, Play accessibility declaration draft, demo script, landing page.

### Security
- Sensitive taps are re-confirmed against the label and container actually pressed, not only the words spoken.
- The accessibility service is declared as an accessibility tool (`isAccessibilityTool`), as required by Google Play for assistive automation.
- Dictated text is stored in the action log only as its length.

### Fixed (from the pre-release review)
- Floating mic always reaches `startForeground` before stopping; it can no longer crash on a missing overlay permission.
- Routing, screen reading and actions run off the main thread; cloud requests are cancelled when the user stops.
- Multi-step plans wait for the next window; scrolling prefers vertical lists over horizontal pagers; password fields are replaced, not appended; silence is reported as "didn't catch that"; long replies are split for the speech engine.

[Unreleased]: https://github.com/abrahamhl/voz/compare/v0.1.0-demo...HEAD
[0.1.0-demo]: https://github.com/abrahamhl/voz/releases/tag/v0.1.0-demo
