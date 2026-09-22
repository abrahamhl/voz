# Architecture

VOZ is two Gradle modules. **`:core`** is pure Kotlin/JVM with zero Android imports and carries all decision logic, so it is fully unit-tested. **`:app`** is the Android shell: accessibility service, voice, UI and adapters. Dependencies are wired by hand (no DI framework).

## Modules

```mermaid
flowchart TB
  subgraph core[":core — pure Kotlin, unit-tested"]
    model["model/<br/>Action (fixed vocabulary), Plan, ScreenNode, Utterance, Lang"]
    parse["parse/<br/>CommandParser (ES/EN/NL grammars), AppMatcher (fuzzy)"]
    route["route/<br/>Router, DecisionEngine interface"]
    plan["plan/<br/>PlanValidator, PlanJson (schema), LocalPlanner"]
    rank["rank/<br/>CommentExtractor, LikeCountParser, CommentRanker"]
    safety["safety/<br/>KillPhrase, SensitiveTargetDetector, ConfirmationReply, UntrustedText"]
  end
  subgraph app[":app — Android"]
    a11y["a11y/<br/>VozAccessibilityService, NodeFinder, ActionExecutor"]
    voice["voice/<br/>SpeechController, TtsController, Earcons, VoiceSession"]
    overlay["overlay/<br/>BubbleService (floating mic)"]
    engine["engine/<br/>LocalEngine, GeminiEngine (BYOK), JevEngine (disabled stub)"]
    flows["flows/<br/>YouTubeFlows (fullscreen, comments)"]
    ui["ui/<br/>Compose: Onboarding, Home, Settings, ActionLog"]
    data["data/<br/>SettingsStore (DataStore), SecretStore (Keystore), ActionLog, InstalledApps"]
  end
  voice --> route
  route --> parse
  route --> plan
  route --> safety
  engine --> route
  engine --> rank
  a11y --> flows
  flows --> rank
  voice --> a11y
  ui --> voice
  overlay --> voice
  voice --> data
```

## One voice turn

```mermaid
sequenceDiagram
  actor U as User
  participant M as Mic (bubble / button / a11y button)
  participant S as VoiceSession
  participant R as SpeechRecognizer (phone)
  participant RT as Router (:core)
  participant E as Engines (local → cloud*)
  participant V as PlanValidator
  participant X as ActionExecutor (a11y)
  participant T as TTS

  U->>M: tap
  M->>S: toggle()
  S->>R: listen (offline preferred)
  R-->>S: text + alternatives
  S->>RT: route(utterance, screen snapshot)
  RT->>RT: kill phrase? grammar? app intent?
  RT->>E: only if not understood
  E-->>RT: plan (JSON, fixed vocabulary)
  RT->>V: validate (≤5 steps, limits, cloud rules)
  V-->>S: ready + confirmations needed
  S->>T: "¿Confirmo? …" (only sensitive targets)
  U->>S: "sí" / "no"
  S->>X: execute each step
  X-->>S: result
  S->>T: short spoken reply
```

\* The cloud engine is used only when the user enabled it **and** saved a key.

## Routing order

1. **Kill phrase** (“para”, “stop”, “stop maar”) → stop immediately.
2. **Offline grammar** for the recognizer’s top hypothesis and up to two alternatives. The utterance language is tried first, then the other two.
3. **App intent**: an installed app name alone opens it; a searchable app name plus words searches inside it.
4. **Decision engines** in order: Gemini (if enabled) → Jev (disabled stub, skipped) → Local heuristics (say a visible label to tap it).
5. **Validator**: action vocabulary, ≤ 5 steps, length limits, no control characters; cloud plans cannot emit `stop` and may only `type` words the user dictated. Taps on sensitive targets need a spoken confirmation, re-checked at runtime against the label actually found on screen.

## Privacy boundaries

```mermaid
flowchart LR
  subgraph phone["On the phone (never leaves)"]
    mic[Microphone audio] --> stt[Phone speech service]
    screen[Screen tree] --> snap[Snapshot on command only]
    log[(Action log)]
    key[(API key — Keystore AES-GCM)]
    comments[YouTube comments] --> ranker[Local ranking]
  end
  subgraph cloud["Only if cloud brain is ON"]
    gem[Google Gemini API]
  end
  snap -- "≤150 text-only items, fenced as untrusted" --> gem
  stt -- "utterance text (not understood offline)" --> gem
  key -- "x-goog-api-key header" --> gem
```

- Audio is handled by the phone’s speech service; VOZ asks for on-device recognition first.
- The screen is read only when a command needs it. Snapshots are not stored.
- Comments are always ranked locally, even with the cloud on.
- The action log is a local JSON-lines file (max 200 entries); dictated text is logged only as its length.

## Testing

- `:core`: JUnit (Jupiter) table-driven tests for every grammar, the router, validator, plan JSON, ranking, extraction and safety, including adversarial cases (prompt injection in screen text, exfiltration via `type`, out-of-vocabulary plans).
- `:app`: node matching on fake view trees, secret-store contract (real AES-GCM on the JVM), settings codec, action log, Gemini engine against a fake HTTP transport.
- CI (GitHub Actions) runs secret scan, tests, Android lint and builds the debug APK on every push.
