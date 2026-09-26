# Business model (hypothesis, not validated)

No price, buyer or reimbursement route below has been tested. Each is marked as an assumption to check in pilots.

## The rule

**Everything a blind, low-vision or motor-impaired person needs to operate their phone is free.** That includes the
offline grammar, every screen action, safety confirmations, the history, all shipped languages, accessibility fixes,
speech-rate and voice settings, and any future hands-free or routine feature that restores basic access.

## What could be paid

| Free (Apache-2.0) | Paid (optional) |
|---|---|
| Android app, `:core` engine, grammars (ES/EN/NL and community languages) | **Organisation support:** setup profiles, training, a support line and an SLA for institutions |
| All actions, safety layer, validator, history | **Managed cloud brain:** a VOZ-operated, paid Gemini backend with a data processing agreement. This is the only way to offer cloud planning to consumers in the EEA under Google’s current Gemini API terms. It needs servers and a legal entity, and it is an owner decision. |
| On-device everything | **OEM licence:** pre-installation on phones for seniors or accessibility lines |

## Revenue hypotheses

1. **Institutions pay, users don’t.** Assistive technology is often funded by public programmes or insurers
   **[VERIFY per country: NL (Wmo/Zvw), ES (regional catalogues)]**. Nobody has been contacted yet.
2. **Managed cloud brain.** Only if pilots show that free-form requests matter to users. Pricing **[VERIFY]**.
3. **Grants** for the free core **[VERIFY eligibility; some programmes require a legal entity or a statement on
   generative-AI use]**.

## Costs

- Device lab and accessibility user research (fixed, per quarter).
- Store compliance: Play accessibility-tool review, microphone foreground-service declaration, privacy documentation.
- Cloud inference only if a managed cloud brain is ever offered.

## What is not the business

No data business: VOZ never sells or monetises user data.
