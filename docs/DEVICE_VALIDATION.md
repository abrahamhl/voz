# VOZ device validation pack (15 minutes)

Everything in this repository has been checked on CI (JVM unit tests, lint, debug build) only. **No step below has been
run on a physical phone yet.** This pack is how a tester produces the first real evidence. Record every result in the
table at the end, including failures. A failure is useful data, not a problem to hide.

- **Time:** about 15 minutes per phone (A + B + C), plus 5 minutes for part D if TalkBack is available.
- **Minimum coverage before any pilot or publication:** two phones, one with Android 13 or later, one with TalkBack on.
- **Build:** the debug APK attached to the latest pre-release (`voz-demo.apk`, check its SHA-256), or `app-debug.apk` from
  the CI run of the commit being tested. Write the tag or commit in the results.
- **Language:** run the commands in the phone's language (Spanish, English or Dutch). Example commands are given in all three.

## A. Install and setup (4 min)

| # | Do | Expect | ✓/✗ |
|---|---|---|---|
| A1 | Install the APK (allow the browser or file manager to install apps). Open VOZ. | Step 1 of 3, "Microphone · Required", one sentence explaining what VOZ is. | |
| A2 | Tap **Allow microphone**, then allow it (and notifications). | Status changes to "Allowed". With TalkBack on, the change is announced. | |
| A3 | Next → **Open accessibility settings** → try to turn on *VOZ voice control*. | Android 13+: a "Restricted setting" dialog appears. Android 12 or older: it turns on. | |
| A4 | (Android 13+) Back in VOZ, follow the four "Restricted setting" steps (App info → ⋮ → Allow restricted settings), then turn the service on. | The service turns on. Back in VOZ the status says "Allowed". Note the exact menu wording on this phone. | |
| A5 | Next → **Allow display over other apps** → allow → come back → **Start using VOZ**. | Home: "VOZ" title, intro line, round mic button, "Floating mic" switch, History and Settings. No warning banners. | |
| A6 | Press system Back on any setup step. | Goes to the previous step. It must not leave the app, except on step 1 of the first setup. | |

## B. Core loop (6 min)

Tap the mic before each command. **Listen for the start tone:** it should play only when the mic is really open. The
button turns yellow only while VOZ is listening.

| # | Say | Expect | ✓/✗ |
|---|---|---|---|
| B1 | "Abre YouTube" / "Open YouTube" / "Open YouTube" | YouTube opens; VOZ says "Opening YouTube". | |
| B2 | Turn on the floating mic (Home switch), go to the phone's home screen, tap the bubble: "Abre ajustes" / "Open settings" / "Open instellingen" | Settings opens. The bubble turns yellow while listening, navy with a stop icon while working. | |
| B3 | "Baja" / "Scroll down" / "Scroll naar beneden", then "Sube" / "Scroll up" | The list scrolls. A short "done" tone plays (no sentence). | |
| B4 | "Lee la pantalla" / "Read the screen" / "Lees het scherm voor". While it reads, tap the bubble. | It starts reading, then stops immediately on the tap. | |
| B5 | "Atrás" / "Go back" / "Ga terug" | One screen back. | |
| B6 | "Busca gatitos en YouTube" / "Search cats on YouTube" / "Zoek katten op YouTube" | YouTube search results for the query. | |
| B7 | Say something VOZ can't do, e.g. "cuéntame un chiste" / "tell me a joke" / "vertel een mop" | VOZ says "I heard '…', but I can't do that yet". Home shows the result and opens "What can I say?". | |
| B8 | Say nothing for 10 seconds. | "I didn't catch that." No error tone. | |
| B9 | Open VOZ → **History**. | Readable sentences grouped under "Today". No `open_app(...)`-style text. Dictated text shows only as "Private text (N characters)". | |

## C. Safety and failure paths (5 min)

| # | Do | Expect | ✓/✗ |
|---|---|---|---|
| C1 | In a chat app with a Send button: "Toca Enviar" / "Tap Send" / "Tik op Verzenden" | VOZ asks "Should I tap 'Send'? Say yes or no". Say **no**. Result: "Cancelled, nothing was done". Nothing is sent. | |
| C2 | Repeat C1, but answer with the **No** button on the VOZ screen (open VOZ during the question). | Cancelled the same way. Then repeat with **Yes**: it taps. | |
| C3 | On a list with one delete button per row (shopping cart, e-mail with several items): "Toca Eliminar" / "Tap Delete" / "Tik op Verwijderen" | VOZ says it sees N buttons and can't tell which one, and taps nothing. | |
| C4 | On a login screen with no field selected: "Escribe hola" / "Type hello" / "Typ hallo" | VOZ asks you to tap the field first and types nothing. | |
| C5 | Settings → Apps → VOZ → Permissions → Microphone → Don't allow. Open VOZ and tap the mic twice. | A banner explains the microphone is blocked, with an **Open app info** button that works. Allow it again afterwards. | |
| C6 | Place the floating mic over a button, then tell VOZ to tap that button. | The app's button is pressed. VOZ does not toggle itself. | |
| C7 | (Computer with adb) `adb shell am force-stop com.google.android.tts`, then give any command. | You hear an error tone or VOZ speaks again after its speech engine restarts. It must never stay silent without any sound. A "VOZ can't speak right now" banner appears while speech is down. | |
| C8 | "Pantalla completa" / "Full screen" on a YouTube video, twice. Then "Sal de pantalla completa" / "Exit full screen". | Stays in full screen or landscape after the second command. After exiting, the phone's own auto-rotate setting is back to what it was. | |
| C9 | Rotate the phone on Home and in Settings. | Nothing is lost or cut off. Landscape Home shows the mic on the left. | |

## D. TalkBack (5 min, if available)

| # | Do | Expect | ✓/✗ |
|---|---|---|---|
| D1 | Turn TalkBack on. On Home, move focus to the mic. | TalkBack reads "VOZ microphone, Ready, button, double-tap to start speaking". | |
| D2 | Double-tap the mic, then say "Atrás" / "Go back" / "Ga terug". | TalkBack's own words are not taken as the command, and VOZ acts. Note whether VOZ and TalkBack talk over each other. | |
| D3 | Focus the floating mic, then use TalkBack actions (swipe up then right, or the actions menu). | "Move left/right/up/down" are available and move the bubble. | |
| D4 | Open Settings and History. | Each screen change is announced by name. Section titles are headings. | |
| D5 | Android's accessibility button (if set up for VOZ): tap it while another app is in front, with the floating mic **off**. | Note what happens: does VOZ listen? Android may block the microphone in this case. | |

## Results

Copy one row per phone. Put the step numbers that failed in "✗", and describe what VOZ said or did in "Notes".

| Date | Build (tag / commit) | Phone model | Android | Skin (One UI, …) | TalkBack | Tester | ✗ steps | Notes |
|---|---|---|---|---|---|---|---|---|
| | | | | | | | | |

Send the results (and screen recordings if possible) to the maintainer. Nothing is uploaded automatically: VOZ has
no analytics.
