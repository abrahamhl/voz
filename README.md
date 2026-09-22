# VOZ

**Talk to your Android phone and it does the rest.** Open and search apps, hear what is on the screen, tap, scroll and type by voice, and ask YouTube for “the funniest comments”. Built first for blind and motor‑impaired people. Offline by default. Open source.

![VOZ demo: saying “read the funniest comments” on a YouTube video](docs/media/demo.gif)

> The demo GIF is a placeholder until the first real-device recording (see [Demo script](docs/DEMO_SCRIPT.md)).

## In 30 seconds

Phones are built for eyes and fingers. Screen readers made them usable without sight, but every task is still dozens of swipes. VOZ adds the missing layer: **say what you want, VOZ does the taps**.

- Tap the floating mic (or the accessibility button) and speak in **Spanish, English or Dutch**.
- A fast **offline grammar** understands everyday commands on any Android 8+ phone. No account, no server.
- VOZ acts through Android’s accessibility service: opens apps, presses buttons by their label, scrolls, types, reads the screen aloud.
- Anything risky (send, pay, buy, delete, call, transfer) is **confirmed out loud first**. Say “para” / “stop” / “stop maar” at any time.
- Optional **cloud brain** (your own Google Gemini key) for free-form requests. Off unless you switch it on.

## Install in 4 steps

1. **Download** `voz-demo.apk` from the [latest release](../../releases/latest).
2. **Allow the install** when Android asks (your browser or file manager needs “Install unknown apps”).
3. **Android 13 and later:** open *Settings → Apps → VOZ → ⋮ (top right) → Allow restricted settings*. Android blocks accessibility services from sideloaded apps until you do this.
4. **Turn on the service:** *Settings → Accessibility → VOZ voice control → On*. Then open VOZ; the 4-step setup checks microphone, floating mic, service and (optional) cloud key.

> This is a debug-signed **demo build**. It has not been published on Google Play.

## Commands cheat-sheet

| What | Español | English | Nederlands |
|---|---|---|---|
| Open an app | «Abre WhatsApp» | “Open WhatsApp” | “Open WhatsApp” |
| Search | «Busca gatitos en YouTube» | “Search cats on YouTube” | “Zoek katten op YouTube” |
| Directions | «Llévame a la estación» | “Navigate to the station” | “Navigeer naar het station” |
| Back / Home | «Atrás» / «Inicio» | “Go back” / “Go home” | “Ga terug” / “Startscherm” |
| Recent apps | «Apps recientes» | “Recent apps” | “Recente apps” |
| Notifications | «Abre las notificaciones» | “Open notifications” | “Toon meldingen” |
| Quick settings | «Ajustes rápidos» | “Quick settings” | “Snelle instellingen” |
| Scroll | «Baja» / «Sube» | “Scroll down” / “Scroll up” | “Naar beneden” / “Naar boven” |
| Tap something | «Toca Suscribirse» | “Tap Subscribe” | “Tik op Abonneren” |
| Type | «Escribe llego en diez minutos» | “Type I’m on my way” | “Typ ik kom eraan” |
| Read the screen | «Lee la pantalla» | “Read the screen” | “Lees het scherm voor” |
| Volume | «Sube el volumen» | “Volume up” | “Volume omhoog” |
| Rotate | «Gira la pantalla» | “Rotate the screen” | “Draai het scherm” |
| Full screen (YouTube) | «Pantalla completa» / «Sal de pantalla completa» | “Full screen” / “Exit full screen” | “Volledig scherm” / “Volledig scherm verlaten” |
| Comments (YouTube) | «Lee los comentarios más populares» | “Read the top comments” | “Lees de populairste reacties” |
| Funny comments | «Lee los comentarios más graciosos» | “Read the funniest comments” | “Lees de grappigste reacties” |
| Comments about X | «Lee los comentarios sobre el final» | “Read comments about the ending” | “Lees de reacties over de gitarist” |
| Stop everything | «Para» | “Stop” | “Stop maar” |

Saying just an app name opens it (“WhatsApp”), and “YouTube lofi” searches inside YouTube. Saying the exact text of a visible button taps it.

## Privacy promise

- **No accounts, no analytics, no ads, no servers.** VOZ has nothing to log in to and nothing phones home.
- Speech is turned into text by your phone’s own speech service; VOZ prefers on-device recognition when available.
- The screen is read **only when you give a command**, and never stored. The action log stays on the phone and can be cleared.
- The cloud brain is **off by default**. When you enable it with your own key, only commands the offline grammar did not understand are sent, with a short text-only summary of the screen (max 150 items). Comments and your key never leave the phone except that request’s key header to Google.

Details: [Privacy](docs/PRIVACY.md) · [Security](SECURITY.md) · [Architecture](docs/ARCHITECTURE.md)

## Status

`v0.1.0-demo`. Every build is compiled, linted and unit-tested in CI (250+ tests). Behaviour on real phones (speech, TalkBack interplay, YouTube labels) still needs the manual checklist in the release notes.

## Roadmap

1. **Real-device hardening:** test matrix on 6 phones (Android 8–16), YouTube label packs per app version, TalkBack co-existence.
2. **Routines and wake-free hands-free mode:** chain commands (“my morning”), long-press hardware shortcut, more apps flows (WhatsApp, Maps).
3. **Pilots:** 2–3 disability organisations in the Netherlands and Spain; accessibility-tool review for Google Play.

## Build from source

Requirements: JDK 17 and the Android SDK (API 37). Then:

```bash
./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`. Modules: `:core` (pure Kotlin: grammar, routing, validation, ranking, safety) and `:app` (Android, Jetpack Compose).

## More

[Pitch](docs/PITCH.md) · [Business model](docs/BUSINESS_MODEL.md) · [Demo script](docs/DEMO_SCRIPT.md) · [Play accessibility declaration (draft)](docs/PLAY_ACCESSIBILITY_DECLARATION.md) · [Contributing](CONTRIBUTING.md) · [Changelog](CHANGELOG.md)

## License

[Apache License 2.0](LICENSE). © 2026 Abraham Haddioui.
