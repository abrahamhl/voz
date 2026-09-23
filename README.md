# VOZ

**Voice control for Android, built for blind, low-vision and motor-impaired people.** Tap the mic (or Android’s
accessibility button), say what you want in **Spanish, English or Dutch**, and VOZ opens apps, searches, taps buttons by
their name, scrolls, types and reads the screen aloud. It asks before anything that sends, pays, deletes or calls.

> **Status: release candidate in progress. Not yet validated on a physical phone.**
> Every commit is compiled, linted and unit-tested in CI. Speech, TalkBack coexistence and behaviour inside real apps
> have not been checked on a device yet. The [device validation pack](docs/DEVICE_VALIDATION.md) is how that happens.
> Until it passes on at least two phones, treat VOZ as a pre-release.

## Why VOZ

Android already has strong free tools: Google Voice Access (tap by visible text, hands-free start), TalkBack voice
commands, and Gemini. VOZ doesn’t claim to replace them. It aims at the gaps a disabled user can actually hit:

- **Dutch.** Google’s Voice Access help page does not list Dutch among its languages (checked 2026-09-23,
  [source](https://support.google.com/accessibility/android/answer/6151848?hl=en)). VOZ understands Dutch, Spanish and
  English commands with an offline grammar.
- **Local first, no account.** The command grammar runs on the phone. VOZ has no accounts, no analytics and no servers.
- **Safety by design.** Screen content is treated as hostile. A fixed list of 19 actions, a spoken or on-screen
  confirmation before risky taps, no guessing between identical buttons, and a re-check of the screen after you say “yes”.
- **Honest feedback.** The mic turns yellow only when it is really listening. Every result is spoken and shown with a
  text, never with colour alone. Failures offer one clear way out.

**Known limits:** you need one tap (or the accessibility button) per command, and VOZ doesn’t listen while it is
talking or working. To stop it, tap the mic. It works best when you say the exact name of a button.

## Install (demo build)

1. **Download** `voz-demo.apk` from the latest pre-release and check its SHA-256.
2. **Allow the install** when Android asks (your browser or file manager needs “Install unknown apps”).
3. **Open VOZ.** Setup has three steps: microphone, accessibility service, and the optional floating mic.
4. **Android 13 and later:** when you try to turn on *VOZ voice control*, Android says “Restricted setting”. VOZ shows
   the four steps to allow it (App info → ⋮ → Allow restricted settings → turn the service on).

> This is a debug-signed demo build. It has not been published on Google Play.

## Commands

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
| YouTube full screen | «Pantalla completa» | “Full screen” | “Volledig scherm” |
| YouTube comments | «Lee los comentarios más populares» | “Read the top comments” | “Lees de populairste reacties” |

Saying just an app name opens it (“WhatsApp”), and “YouTube lofi” searches inside YouTube. The YouTube commands depend
on labels inside the YouTube app that have not been checked on a device yet.

## Privacy

- **No accounts, no analytics, no ads, no servers of our own.**
- Your phone’s speech service turns your voice into text. VOZ asks for on-device recognition first; if the language
  pack is missing, the phone’s online recognition may be used (that is Google’s or your phone maker’s service, not VOZ).
- The screen is read **only when you give a command** and is not stored. The history (last 200 entries) stays on the
  phone; dictated text is stored only as a character count. Backups and device-to-device transfer are turned off.
- **Cloud brain (developer and pilot builds only).** Google’s Gemini API terms allow it only for professional use by
  adults, and only with paid keys for users in the EEA, Switzerland and the UK, so public release builds switch it off at build time (`cloud_brain_available=false`) and drop the internet permission; CI verifies both on every release build, so a release build cannot reach the network.
  In pilot builds it is off by default and needs your own key plus an explicit consent screen. When it is on, only
  commands VOZ can’t handle on the phone are sent, with the screen text, and e-mail addresses, IBANs and long numbers
  masked first.

Details: [Privacy](docs/PRIVACY.md) · [Security](SECURITY.md) · [Compliance](docs/COMPLIANCE.md) · [Architecture](docs/ARCHITECTURE.md)

## What is verified

| Area | Evidence |
|---|---|
| Grammar, routing, validation, safety rules | JVM unit tests in CI (300+) |
| App logic (tap resolution, confirmations, settle timing, bubble bounds, consent gate) | JVM unit tests in CI |
| Build, lint (0 errors), secret scan | CI on every commit |
| Speech recognition, text-to-speech, taps in real apps, TalkBack coexistence, Android 13+ install path | **Not yet verified** · [device validation pack](docs/DEVICE_VALIDATION.md) |
| Use by disabled people | **None yet**. No user study has been run. |

## Build from source

Requirements: JDK 17 and the Android SDK. Then:

```bash
./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

The debug APK (with the pilot-only cloud brain) lands in `app/build/outputs/apk/debug/`. Release builds switch the
cloud brain off at build time and drop the internet permission. Modules: `:core` (pure Kotlin: grammar, routing,
validation, ranking, safety) and `:app` (Android, Jetpack Compose).

## More

[Device validation](docs/DEVICE_VALIDATION.md) · [Pitch](docs/PITCH.md) · [Business model](docs/BUSINESS_MODEL.md) ·
[Demo script](docs/DEMO_SCRIPT.md) · [Play accessibility declaration (draft)](docs/PLAY_ACCESSIBILITY_DECLARATION.md) ·
[Contributing](CONTRIBUTING.md) · [Code of conduct](CODE_OF_CONDUCT.md) · [Changelog](CHANGELOG.md)

## License

[Apache License 2.0](LICENSE). © 2026 Abraham Haddioui.
