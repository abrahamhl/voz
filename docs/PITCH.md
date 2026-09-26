# VOZ — one-page pitch

**VOZ is a voice-control tool for Android, built for blind, low-vision and motor-impaired people. It works in Dutch,
Spanish and English, runs its command grammar on the phone, and asks before anything risky.**

> Evidence status (September 2026): release candidate, CI-verified only. Not yet run on a physical phone, and not yet
> used by any disabled person. Every claim below is labelled with what supports it.

## Problem

- At least **2.2 billion people** live with a near or distance vision impairment
  ([WHO](https://www.who.int/news-room/fact-sheets/detail/blindness-and-visual-impairment)). **1.3 billion** experience
  a significant disability ([WHO](https://www.who.int/news-room/fact-sheets/detail/disability-and-health)); about
  **90 million** live in the EU
  ([European Commission](https://commission.europa.eu/strategy-and-policy/policies/justice-and-fundamental-rights/disability/union-equality-strategy-rights-persons-disabilities-2021-2030_en)).
- An estimated **3.5 billion people will need assistive technology by 2050**
  ([WHO](https://www.who.int/news-room/fact-sheets/detail/assistive-technology)).
- Free tools exist and are good: Google Voice Access, TalkBack voice commands, Gemini. But gaps remain for specific
  users. Voice Access, for example, does not list Dutch among its languages
  ([Google help, checked 2026-09-23](https://support.google.com/accessibility/android/answer/6151848?hl=en)).

## Solution

1. **Say it** (ES / EN / NL): “Busca gatitos en YouTube”, “Tap Subscribe”, “Lees het scherm voor”.
2. **VOZ does it:** opens apps, presses buttons by name, scrolls, types, reads the screen, controls volume and rotation.
3. **Safety first:** 19 fixed actions; confirmation before send, pay, delete, call, subscribe and similar actions; no
   guessing between identical buttons; the screen is re-checked after “yes”; screen text is treated as hostile.
4. **Honest UI:** the mic turns yellow only when it is really listening; every result is spoken and shown as text; each
   error has one way out.

## Why now

- **Policy:** Google Play forbids Accessibility-API automation that plans and acts on its own, *except* for verified
  accessibility tools whose core purpose is assisting people with disabilities
  ([Play policy](https://support.google.com/googleplay/android-developer/answer/10964491)). VOZ is designed to qualify.
- **On-device AI has limits:** Gemini Nano through ML Kit GenAI is beta, runs on a list of recent devices, and is
  blocked when the app is not in the foreground ([ML Kit GenAI](https://developers.google.com/ml-kit/genai)). A
  screen-level tool that acts on other apps needs a different design, and VOZ’s offline grammar is one.
- **Android** is the majority mobile platform worldwide
  ([StatCounter](https://gs.statcounter.com/os-market-share/mobile/worldwide)).

## Wedge (to be proven)

- **Dutch-speaking motor-impaired users** first: the one gap with a public source today.
- **Local and account-free:** no Google account or cloud needed for the command grammar.
- **Auditable** once the repository is public: fixed action list, local validator, readable history.

## Plan (closed cycles)

| Cycle | Goal | Proof point |
|---|---|---|
| 1. Device validation | 15-minute pack on ≥2 phones (one Android 13+, one with TalkBack) | Results table in the device matrix |
| 2. First users | 6 Dutch- or Spanish-speaking testers with motor or visual impairments, 14 days | ≥3 of 6 still use VOZ on day 14 |
| 3. Distribution | Google Play accessibility-tool review, or organisation-managed installs | Listing approved, or one organisation letter of intent |

Kill criteria: the core loop fails on 2 of 3 phones after 3 fix rounds, fewer than 3 of 6 testers keep using it, or
Voice Access ships Dutch before VOZ has 10 users.

## Ask

- **Pilot partners:** disability organisations and rehabilitation centres in the Netherlands and Spain.
- **Funding:** **[OWNER TO SET]**, only after cycle 1 passes.
- **Advisors:** accessibility policy (Play review), assistive-technology procurement.

Contact: via the GitHub profile [@abrahamhl](https://github.com/abrahamhl).
