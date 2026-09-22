# Security policy

## Reporting a vulnerability

Please report security issues privately through GitHub’s **“Report a vulnerability”** (Security → Advisories) on this repository, or by contacting the maintainer via [@abrahamhl](https://github.com/abrahamhl). Do not open a public issue for security problems. We aim to acknowledge reports within 7 days.

## Threat model (summary)

VOZ holds an accessibility service, which can read the screen and act on it. The design assumes that **screen content is hostile**: any web page, message or comment can contain text meant to manipulate an AI.

| Threat | Mitigation |
|---|---|
| Prompt injection via screen text (“ignore previous instructions…”) | Screen text is sent to the cloud only when enabled, fenced between delimiters, delimiter sequences neutralised, suspicious lines flagged; the model’s output is re-validated on the phone |
| Model asks to perform arbitrary actions | Fixed action vocabulary (19 verbs), ≤ 5 steps, strict JSON parsing: anything unknown rejects the whole plan |
| Exfiltration by typing secrets into a field | Cloud plans may only `type` words the user actually dictated |
| Destructive or costly taps (send, pay, buy, delete, call, transfer) | Spoken “¿Confirmo?” in ES/EN/NL before the tap, re-checked against the label really found on screen; unclear answer = no |
| Runaway execution | Kill phrase (“para” / “stop” / “stop maar”) and tapping the mic stop speech and the running plan |
| API key theft | Key encrypted with AES-256-GCM under a non-exportable Android Keystore key; never logged; sent only as a header over HTTPS |
| Secrets in the repository | CI secret scan on every push; no keys in code, CI or logs |

## Supported versions

Only the latest release receives fixes during the demo phase.
