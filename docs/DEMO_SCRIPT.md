# 90-second demo video script

**Goal:** show that a person can run a phone by voice, end-to-end, and finish on the “wow”: YouTube comments read aloud.
**Setup:** Android phone with VOZ installed and set up, floating mic visible, YouTube logged out or on a neutral account, phone volume at 60 %, screen recording on (with microphone audio). Language: Spanish (repeat in English and Dutch for other markets). Optional: TalkBack on to show co-existence.

| Time | On screen | Voice (user) | VOZ says / does |
|---|---|---|---|
| 0:00–0:08 | Home screen, floating mic on the right. Caption: *“2.2 billion people live with a vision impairment. Phones still expect eyes and fingers.”* | — | — |
| 0:08–0:18 | Tap the floating mic (it turns red). | «Abre YouTube» | “Abriendo YouTube.” YouTube opens. |
| 0:18–0:30 | Mic again. | «Busca Rosalía en YouTube» | “Buscando Rosalía en YouTube.” Results appear. |
| 0:30–0:42 | Mic again. | «Lee la pantalla» | Reads the first result titles aloud. |
| 0:42–0:50 | Mic again. | «Toca» + first video title (e.g. «Toca Despechá») | “He pulsado Despechá.” Video plays. |
| 0:50–0:58 | Mic again. | «Pantalla completa» | Player goes full screen. |
| 0:58–1:20 | Mic again. Caption: *“The wow moment.”* | «Sal de pantalla completa» then «Lee los comentarios más graciosos» | Opens comments, scrolls 5 times, reads: “Comentario 1. Me gusta: 1.234. …” (three comments). |
| 1:20–1:26 | Show a chat app with a Send button. Mic. | «Toca Enviar» | “¿Confirmo? Voy a pulsar «Enviar». Di sí o no.” User: «No». “Cancelado.” |
| 1:26–1:30 | VOZ home → Action log. Caption: *“Offline core. Open source. Built for accessibility first.”* End card: VOZ logo + repo link. | «Para» | “Parado.” |

## Tips

- Speak naturally; do not pause between words. If a command is misheard, keep it in the cut: the spoken feedback is part of the story.
- Keep the comment reading to three comments; trim silence in editing, not the TTS itself.
- Export a 480 px wide, 12 fps GIF of 0:58–1:20 as `docs/media/demo.gif` for the README.
- For the Play accessibility declaration video, add the onboarding and the “Allow restricted settings” step.
