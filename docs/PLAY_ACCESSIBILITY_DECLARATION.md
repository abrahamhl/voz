# Google Play — AccessibilityService declaration (DRAFT)

> **Status: draft. Verify against the current Google Play policy before submitting.** Policy reviewed on 2026-09-23: [Use of the AccessibilityService API](https://support.google.com/googleplay/android-developer/answer/10964491). Play Console wording and fields change; re-read the policy and the Permissions Declaration form at submission time.

## What the policy says (summary, 2026-09-23)

- Only services **designed to help people with disabilities** access their device, or otherwise overcome challenges stemming from their disabilities, may declare themselves accessibility tools via the `isAccessibilityTool` attribute.
- Apps declaring `isAccessibilityTool` are exempt from the prominent disclosure and consent requirement.
- Using the Accessibility API to **autonomously initiate, plan and execute actions** is prohibited, **except** for verified accessibility tools, when that functionality serves the app’s core purpose of assisting people with disabilities.
- All other Play policies still apply (User Data, Malware, Device and Network Abuse, Deceptive Behavior).

## VOZ configuration

- `res/xml/accessibility_service_config.xml` declares `android:isAccessibilityTool="true"`.
- The service description shown in system settings explains what VOZ does and that it acts only on the user’s voice command.
- Onboarding explains the service before sending the user to settings, even though accessibility tools are exempt from the disclosure requirement.

## Draft answers for the Permissions Declaration form

**Core purpose (≤ 500 characters):**
> VOZ is a voice control app designed for blind, low-vision and motor-impaired people who cannot easily use a touchscreen. The user speaks a command (for example “open WhatsApp”, “read the screen”, “tap Send”) and VOZ performs the taps, scrolls and typing on their behalf through the AccessibilityService, and reads results aloud. Every action is triggered by an explicit spoken request.

**Why the AccessibilityService API is required:**
> To read the visible text of the current screen for the user, to find a control by its label and activate it, to scroll lists, to type dictated text into the focused field, and to perform global actions (back, home, recents, notifications, quick settings). No other Android API provides this for arbitrary apps.

**Is the app an accessibility tool?**
> Yes. Its primary purpose is to give people with visual and motor disabilities hands-free, eyes-free control of their phone. It is designed to work alongside TalkBack, uses large touch targets and a high-contrast mode, and supports Spanish, English and Dutch.

**Autonomous functionality:**
> VOZ never acts on its own initiative. It plans actions only in response to the user’s spoken request, limited to a fixed vocabulary of 18 action types and at most 5 steps. Actions on sensitive targets (send, pay, buy, delete, call, transfer) require a spoken confirmation. The user can stop any action by saying “stop” or tapping the microphone. An on-device action log shows everything VOZ did.

**Data handling:**
> VOZ has no accounts, analytics or servers. Screen content is read in memory only to execute the user’s command and is not stored or transmitted, except when the user explicitly enables the optional cloud assistant with their own API key; then only a text-only summary of the current screen and the command are sent to the user’s chosen provider.

## Video demonstration (required by the form)

Record following [DEMO_SCRIPT.md](DEMO_SCRIPT.md), showing: onboarding explanation, enabling the service, three voice commands, a sensitive-action confirmation, and the action log.

## Open questions for the owner

- [VERIFY] Whether Play requires additional evidence (organisation letters, user research) to accept the accessibility-tool declaration.
- [VERIFY] Whether the optional cloud assistant needs its own Data safety entries (likely yes: “App activity / other user-generated content”, shared with the user’s chosen provider, optional).
- [VERIFY] Whether a consumer “VOZ Plus” subscription affects the accessibility-tool status (the free core must remain the primary purpose).
