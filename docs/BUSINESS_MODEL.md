# Business model

## The rule

**Everything a blind or motor-impaired person needs to operate their phone is free, forever.** That includes the offline grammar, every screen action, the screen reader integration, safety confirmations, the action log, all languages we ship and future accessibility fixes. No feature that restores basic access will ever move behind a paywall.

## Open-core boundary

| Free and open source (Apache-2.0) | Paid (optional, separately licensed) |
|---|---|
| Android app, `:core` engine, offline grammars (ES/EN/NL and community languages) | **VOZ Plus**: managed premium cloud brain (no own key needed), natural neural voices, routines and multi-step shortcuts, priority language packs |
| All actions: open, search, tap, type, scroll, read screen, volume, rotate, YouTube flows | **Organisation console** (B2B/B2G): remote setup profiles, device fleet configuration, anonymous aggregate usage reports for funders (opt-in) |
| Bring-your-own-key cloud planner (Gemini today; pluggable engines) | **OEM / SDK licence**: pre-installation, deeper system integration, white-label |
| Safety layer, validator, action log | Support and SLA for institutions |

The boundary is simple: **access is free, convenience and scale are paid.** A blind user with a €100 phone and no key gets the same core as everyone else.

## Revenue streams

1. **VOZ Plus subscription (consumers who want more).** Premium cloud brain without managing API keys, natural voices, routines. Price to test in pilots: **[VERIFY with user research]** per month; discounted or free through organisations.
2. **B2B / B2G licensing.** Disability organisations, public assistive-technology programmes, rehabilitation centres and insurers license the organisation console and support. Assistive technology is often procured and reimbursed by public bodies or insurers, which makes institutional buyers the natural payer **[VERIFY reimbursement routes per country: NL (WMO/Zvw), ES (catálogo ortoprotésico / CCAA)]**.
3. **OEM licensing.** Phone makers targeting seniors and accessibility-focused lines license pre-installation and deeper integration.
4. **Grants.** Accessibility and open-source funds (e.g. EU and national innovation programmes, disability foundations) finance core work that stays free **[VERIFY eligibility and calls]**.

## Costs that scale

- Cloud inference only for Plus users (BYOK users pay their own provider directly).
- Device lab and accessibility user research (fixed, per quarter).
- Store compliance: Google Play accessibility-tool review and privacy documentation.

## Why this can work

- The free core builds trust with users and organisations, which is the real distribution channel in assistive technology.
- Institutions need auditability, privacy and support, which open source plus a paid console provides.
- No data business: VOZ never sells or monetises user data. That is a feature for buyers, not a constraint.
