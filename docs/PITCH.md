# VOZ — one-page pitch

**VOZ lets anyone run an Android phone by voice: open apps, hear the screen, tap, type and even “read me the funniest comments”. Accessibility-first, works offline on any Android 8+ phone, auditable because it is open source.**

## Problem

- At least **2.2 billion people** live with a near or distance vision impairment ([WHO, Blindness and vision impairment](https://www.who.int/news-room/fact-sheets/detail/blindness-and-visual-impairment)). **1.3 billion** (16 % of humanity) experience a significant disability ([WHO, Disability](https://www.who.int/news-room/fact-sheets/detail/disability-and-health)); **90 million** of them live in the EU ([European Commission](https://commission.europa.eu/strategy-and-policy/policies/justice-and-fundamental-rights/disability/union-equality-strategy-rights-persons-disabilities-2021-2030_en)).
- An estimated **3.5 billion people will need assistive technology by 2050**, and in some low-income countries as few as **3 %** have access to what they need ([WHO, Assistive technology](https://www.who.int/news-room/fact-sheets/detail/assistive-technology)).
- Screen readers made phones usable without sight, but they are **navigation tools**: every task is still dozens of swipes and gestures. For people with tremor, paralysis or fatigue, touch itself is the barrier.

## Solution

VOZ is a voice layer on top of Android’s accessibility service:

1. **Say it** (ES / EN / NL): “Busca gatitos en YouTube”, “Tap Subscribe”, “Lees het scherm voor”.
2. **VOZ does the taps**: opens apps, presses buttons by their visible label, scrolls, types, reads the screen aloud, controls volume and rotation.
3. **Wow moment:** on any YouTube video, “read the funniest comments” opens the panel, scrolls, dedupes, ranks by laughter and likes, and reads the top three.
4. **Safety built in:** risky targets (send, pay, buy, delete, call, transfer) require a spoken “yes”; “para/stop” halts everything; screen text is treated as untrusted data; an on-device action log shows exactly what happened.

## Why now

- **The OS is opening up to agents.** Android’s new AppFunctions API lets apps expose actions “like on device MCP servers” to agents and assistants — still an experimental preview ([Android Developers](https://developer.android.com/ai/appfunctions)). The direction is clear; the coverage is not there yet, so screen-level control remains the universal path.
- **On-device AI is real but premium-only.** Gemini Nano through ML Kit GenAI runs on a list of recent flagship devices (Pixel 9–11, Galaxy S25/S26 and similar) ([Google ML Kit GenAI](https://developers.google.com/ml-kit/genai)). Most disabled users do not own those phones.
- **Android is the majority platform:** 67.6 % of mobile OS share worldwide in August 2026 ([StatCounter](https://gs.statcounter.com/os-market-share/mobile/worldwide)).
- **Policy favours real accessibility tools.** Google Play forbids autonomous agents built on the Accessibility API *except* verified accessibility tools whose core purpose is assisting people with disabilities ([Play policy](https://support.google.com/googleplay/android-developer/answer/10964491)). VOZ is designed to qualify; generic “AI agent” apps are not.

## Wedge

- **Accessibility-first, not an AI demo:** built with and for blind and motor-impaired users, TalkBack-friendly, large targets, high contrast.
- **Any Android, offline core:** the grammar and ranking run locally in milliseconds on an Android 8 phone; the cloud is optional (bring your own key).
- **Auditable:** Apache-2.0, fixed action vocabulary, local validator, on-device action log. Organisations can inspect exactly what it may do.
- **Multilingual from day one:** Spanish, English and Dutch grammars, extensible per language.

## Traction plan (next 6 months)

| Cycle | Goal | Proof point |
|---|---|---|
| 1. Real-device hardening | 6-phone test matrix, YouTube label packs, TalkBack co-existence | 15-minute checklist passes on 5 of 6 phones |
| 2. Pilots | 2–3 disability organisations (NL, ES), 30 daily users | Weekly active use, tasks completed per session, time saved vs. screen reader alone |
| 3. Distribution | Google Play accessibility-tool review, F-Droid, organisation-managed installs | Listing approved; first B2B/B2G letter of intent |

## Ask

- **Pilot partners:** disability organisations and rehabilitation centres in the Netherlands and Spain.
- **Funding:** pre-seed or grant of **[OWNER TO SET]** to fund 12 months (one Android engineer, accessibility user research, device lab).
- **Advisors:** accessibility policy (Play review), assistive-technology procurement (B2G).

Contact: via the GitHub profile [@abrahamhl](https://github.com/abrahamhl).
