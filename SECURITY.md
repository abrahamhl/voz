# Security policy

## Reporting a vulnerability

While the repository is private, report security issues directly to the maintainer via
[@abrahamhl](https://github.com/abrahamhl) on GitHub. Once it is public, use GitHub’s **“Report a vulnerability”**
(Security → Advisories). Please don’t open a public issue for security problems. We aim to acknowledge reports
within 7 days.

## Threat model (summary)

VOZ holds an accessibility service, which can read the screen and act on it. The design assumes that **screen content
is hostile**: any web page, message or comment can contain text meant to manipulate an AI, or to trick VOZ into
pressing the wrong thing.

| Threat | Mitigation |
|---|---|
| Prompt injection via screen text (“ignore previous instructions…”) | The cloud planner exists only in developer/pilot builds and runs only after explicit consent. Screen text is fenced between delimiters, delimiter sequences are neutralised, instruction-like lines are flagged, personal data (e-mail, IBAN, codes, phone and card numbers) is masked. The model’s output is re-validated on the phone. |
| The model asks for arbitrary actions | Fixed action vocabulary (19 verbs), at most 5 steps, strict JSON parsing: anything unknown rejects the whole plan. |
| The model presses something the user never asked for | A cloud tap on a label the user did not say, or a cloud search for text the user did not say, needs a spoken or on-screen “yes”. |
| The model speaks attacker text in VOZ’s voice (“your account is locked, call…”) | Cloud replies with digits, links, e-mail addresses, sensitive verbs or instruction-like text are dropped. The rest is prefixed “Gemini says”. |
| Exfiltration by typing secrets into a field | Cloud plans may only `type` words the user actually dictated. With several fields and none focused, VOZ asks instead of guessing. |
| Destructive or costly taps (send, pay, buy, delete, call, transfer, subscribe, join, rent, allow, install, accept, confirm, share, post, empty trash, prices) | Confirmation in ES/EN/NL before the tap. The check covers the matched label, the pressed container and the texts inside it. An unclear answer, a timeout or a question that could not be spoken counts as no. |
| The screen changes while the user answers | After “yes”, VOZ re-reads the screen and presses only if the same target is still in the same place. |
| Identical buttons (one “Delete” per row) | VOZ refuses to guess for anything that needs confirmation and says how many it sees. |
| VOZ taps its own floating mic | Injected gestures pass through the bubble. |
| Runaway execution | Tapping the mic, the floating mic or the accessibility button stops speech and the running plan at any time. The kill phrase (“para” / “stop” / “stop maar”) works as a command or as the answer to a question. VOZ doesn’t listen while it is talking or working, so the tap is the reliable stop. |
| API key theft (pilot builds) | The key is encrypted with AES-256-GCM under a non-exportable Android Keystore key. It is never logged, never backed up or transferred, and only sent as a header over HTTPS. |
| Data leaving the phone | No analytics or servers. History stays on the phone. Dictated or unrecognised speech is stored only as a character count. Backups and device-to-device transfer are excluded. |
| Secrets in the repository | CI secret scan on every push. No keys in code, CI or logs. |

## Supported versions

Only the latest pre-release receives fixes during the release-candidate phase.
