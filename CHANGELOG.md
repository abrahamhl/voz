# Changelog

All notable changes to this project are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses [Semantic Versioning](https://semver.org/).

## [Unreleased]

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
- CI: secret scan, 262 unit tests, Android lint and debug APK on every push; release workflow attaching `voz-demo.apk` to tagged releases.
- Documentation: README, pitch, business model, architecture, privacy, security, Play accessibility declaration draft, demo script, landing page.

[Unreleased]: https://github.com/abrahamhl/voz/compare/v0.1.0-demo...HEAD
[0.1.0-demo]: https://github.com/abrahamhl/voz/releases/tag/v0.1.0-demo
