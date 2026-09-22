# Privacy

VOZ is built for people who depend on it to use their phone. Their trust matters more than any metric, so the design collects nothing.

## What VOZ does not do

- No accounts, no sign-in.
- No analytics, crash reporting or advertising SDKs.
- No VOZ servers. The app has no backend to send anything to.
- No selling or sharing of data, ever.

## What stays on your phone

| Data | Where | How long |
|---|---|---|
| Settings (language, speech rate, bubble size, contrast, cloud switch) | App-private DataStore | Until you uninstall |
| Action log (what VOZ heard and did) | App-private file, max 200 entries | Until you clear it or uninstall. Dictated text is stored only as its length |
| Your cloud API key (optional) | App-private storage, encrypted with an AES-256-GCM key held in the Android Keystore | Until you delete it in Settings or uninstall |
| Screen content | Read in memory only when a command needs it | Discarded after the command |

## Microphone

VOZ listens only after you tap the mic (in-app button, floating mic or accessibility button). Speech is converted to text by your phone’s speech recognition service; VOZ asks for on-device recognition first. The floating mic runs as a visible foreground service with a notification so you always know it is available.

## Accessibility service

The service is how VOZ reads the screen and taps for you. It acts only in response to your command, and it never records, stores or uploads what it reads, except as described under “Cloud brain” when you turn that on.

## Cloud brain (optional, off by default)

If you save your own Google Gemini API key **and** switch the cloud on:

- Only commands the offline grammar did not understand are sent.
- The request contains your words and a short **text-only** summary of the current screen (at most 150 items, each cut to 120 characters), clearly marked as untrusted data.
- Your key is sent only as a request header to Google’s API.
- YouTube comments are never sent; ranking always happens on the phone.
- Google’s own terms and privacy policy apply to that request.

Switch it off or delete the key at any time in Settings.

## Internet permission

The app declares internet access only for the optional cloud brain and for opening web search results in your browser.

## Contact

Questions or concerns: open an issue in the repository or contact the maintainer via the GitHub profile [@abrahamhl](https://github.com/abrahamhl).
