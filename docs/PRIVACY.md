# Privacy

VOZ is built for people who depend on it to use their phone. Their trust matters more than any metric, so the design
collects nothing.

## What VOZ does not do

- No accounts, no sign-in.
- No analytics, crash reporting or advertising SDKs.
- No VOZ servers. The app has no backend to send anything to.
- No selling or sharing of data.

## What stays on your phone

| Data | Where | How long |
|---|---|---|
| Settings (language, speech rate, bubble size, contrast, cloud switch and consent version) | App-private DataStore | Until you uninstall |
| History (what VOZ heard and did, as readable sentences) | App-private file, max 200 entries | Until you clear it or uninstall. Dictated or unrecognised speech is stored only as a character count |
| Your Gemini API key (pilot builds only, optional) | App-private storage, encrypted with an AES-256-GCM key held in the Android Keystore | Until you delete it in Settings or uninstall |
| Screen content | Read in memory only when a command needs it | Discarded after the command |

Cloud backup and device-to-device transfer of all app data are turned off.

## Microphone

VOZ listens only after you tap the mic (in the app, the floating mic, or Android’s accessibility button). Your phone’s
speech recognition service turns speech into text. VOZ asks for on-device recognition first. If the language pack is
missing, the phone’s online recognition may be used; that service belongs to Google or your phone maker, not to VOZ.
The floating mic runs as a visible foreground service with a notification, so you always know it is available.

## Accessibility service

The service is how VOZ reads the screen and taps for you. It acts only when you give a command. It never records,
stores or uploads what it reads, except as described under “Cloud brain” in pilot builds when you turn that on.

## Cloud brain (developer and pilot builds only, off by default)

Public release builds do not contain the cloud brain. Google’s Gemini API terms allow the API only for professional use
by adults (18 or older), and only with paid keys for users in the EEA, Switzerland and the UK
([Gemini API Additional Terms](https://ai.google.dev/gemini-api/terms)).

In a pilot build, nothing is sent unless you save your own Gemini API key, switch the cloud brain on **and** accept the
consent screen. Then:

- Only commands VOZ can’t handle on the phone are sent. The on-phone brains are always asked first.
- The request contains your words and the text on the current screen (at most 150 items, each cut to 120 characters),
  marked as untrusted data. E-mail addresses, IBANs, one-time codes and phone and card numbers are replaced by
  placeholders before sending.
- The name of the app in front, your voice, your contacts and YouTube comments are never sent.
- Your key is sent only as a request header to Google’s API over HTTPS.
- Google’s terms apply to that request. With an unpaid key, Google may use what is sent to improve its products and
  people may read it; with a paid key, Google says it does not use prompts or responses to improve its products.

Switch it off or delete the key at any time in Settings.

## Internet permission

The app declares internet access only for the pilot cloud brain and for opening web search results in your browser.

## Who is responsible

VOZ is published by Abraham Haddioui. VOZ itself receives no personal data. If you use a pilot build’s cloud brain,
you send data to Google under your own agreement with Google.

## Contact

Questions or concerns: contact the maintainer via the GitHub profile [@abrahamhl](https://github.com/abrahamhl).
