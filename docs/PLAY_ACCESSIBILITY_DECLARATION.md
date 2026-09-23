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
> VOZ never acts on its own initiative. It plans actions only in response to the user’s spoken request, limited to a fixed vocabulary of 19 action types and at most 5 steps. Actions on sensitive targets (send, pay, buy, delete, call, transfer, subscribe, install, share and similar, and any price) require a spoken or on-screen confirmation, and VOZ refuses to guess between identical buttons. The user can stop any action by tapping the microphone, the floating mic or the accessibility button. An on-device history shows everything VOZ did.

**Data handling:**
> VOZ has no accounts, analytics or servers. Screen content is read in memory only to execute the user’s command and is not stored or transmitted. The Play build does not contain the cloud assistant that exists in developer and pilot builds (Gemini API terms restrict it to professional use and, in the EEA/CH/UK, to paid keys).

## Video demonstration (required by the form)

Record following [DEMO_SCRIPT.md](DEMO_SCRIPT.md), showing: onboarding explanation, enabling the service, three voice commands, a sensitive-action confirmation, and the action log.

## Open questions for the owner

- [VERIFY] Whether Play requires additional evidence (organisation letters, user research) to accept the accessibility-tool declaration.
- Data safety (Play build): no data collected or shared; no data leaves the device. Re-check if a cloud feature is ever added.
- [VERIFY] Whether a consumer “VOZ Plus” subscription affects the accessibility-tool status (the free core must remain the primary purpose).

## Microphone foreground service declaration (Play Console)

The floating mic is a `microphone` foreground service (Android 14+ requires the type, the
`FOREGROUND_SERVICE_MICROPHONE` permission and a granted `RECORD_AUDIO`; it can only start while the app is visible).
Play Console asks for a description and a video of the feature
([Play help](https://support.google.com/googleplay/android-developer/answer/13392821)). Draft description:

> The floating mic lets a person who cannot easily use the touchscreen start a voice command while another app is in
> front. The microphone is used only after the user taps the floating mic, for one command at a time; a persistent
> notification shows the feature is on and offers “Hide mic”.

## Prominent disclosure

Although accessibility tools are exempt, the onboarding accessibility step works as an in-app disclosure before the
user is sent to system settings: it says what VOZ reads (screen text), when (only on a command), and that it keeps and
sends nothing. The Play User Data policy asks for that disclosure before permission requests
([Play help](https://support.google.com/googleplay/android-developer/answer/10144311)).
