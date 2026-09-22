package dev.auxdesign.voz.core.parse

import dev.auxdesign.voz.core.model.Action
import dev.auxdesign.voz.core.model.CommentMode
import dev.auxdesign.voz.core.model.Direction
import dev.auxdesign.voz.core.model.GlobalKind
import dev.auxdesign.voz.core.model.SearchTarget

/*
 * Grammars are ordered: specific rules first (volume before scroll, notifications before open...).
 * Patterns are written against folded text: lowercase, no accents, punctuation turned into spaces.
 */

private fun topic(c: Captures): Action? = c.raw("p")?.let { Action.ReadComments(CommentMode.TOPIC, topic = it) }

private fun tap(c: Captures): Action? = c.raw("p")?.let { Action.Tap(it) }

private fun type(c: Captures): Action? = c.dictated("p")?.let { Action.Type(it) }

internal object EsGrammar {
    private const val T =
        "youtube|you tube|yutub|google|internet|la web|la red|google maps|maps|los mapas|el mapa|mapas|mapa|" +
            "la play store|play store|la tienda|tienda|google play"
    private const val C = "comentarios|comentario"

    val rules: List<Rule> = listOf(
        Rule("(?:sube|subir|subele|aumenta|aumentar|dale mas) (?:el )?volumen|mas volumen|volumen (?:arriba|mas alto)|mas alto|mas fuerte") {
            Action.Volume(Direction.UP)
        },
        Rule("(?:baja|bajar|bajale|disminuye|disminuir|reduce|reducir) (?:el )?volumen|menos volumen|volumen (?:abajo|mas bajo)|mas bajo|mas flojo") {
            Action.Volume(Direction.DOWN)
        },
        Rule("(?:sal|salir|salte|quita|quitar|cierra|cerrar|desactiva|desactivar) (?:de )?(?:la )?pantalla completa|pantalla normal") {
            Action.Fullscreen(enter = false)
        },
        Rule("(?:(?:pon|poner|ponlo|ponla|activa|activar|ver|verlo|mira|mirar)(?: en| a)? )?(?:la )?pantalla completa|maximiza(?:r)?(?: el video)?") {
            Action.Fullscreen(enter = true)
        },
        Rule("(?:lee(?:me)?|leer|dime) (?:los )?(?:$C) (?:mas )?(?:graciosos|divertidos|chistosos|comicos)|(?:los )?(?:$C) mas (?:graciosos|divertidos)") {
            Action.ReadComments(CommentMode.FUNNY)
        },
        Rule("(?:lee(?:me)?|leer|dime) (?:los )?(?:$C) (?:sobre|acerca de|que hablan de|que hablen de|que mencionan|con) (?<p>.+?)", ::topic),
        Rule("que dicen (?:los (?:$C) |en los (?:$C) )?(?:sobre|de|acerca de) (?<p>.+?)", ::topic),
        Rule(
            "(?:lee(?:me)?|leer|dime) (?:los )?(?:$C)(?: mas)?(?: (?:populares|votados|destacados|top|mejores|importantes))?(?: del? (?:video|videos))?|" +
                "(?:los )?mejores (?:$C)|(?:abre|abrir|muestra|muestrame|mostrar|ver) (?:los )?(?:$C)|que dicen (?:en )?los (?:$C)",
        ) { Action.ReadComments(CommentMode.POPULAR) },
        Rule(
            "(?:lee(?:me)?|leer|describe|describeme) (?:(?:la|toda la|lo que hay en la|lo que hay en|lo que pone en la|lo que pone en|lo que sale en la) )?pantalla|" +
                "que (?:hay|pone|sale|aparece|ves) en (?:la )?pantalla|que pone|que hay|que ves|lee(?:me)? (?:esto|todo|lo que hay)|donde estoy",
        ) { Action.ReadScreen },
        Rule("(?:(?:abre|abrir|muestra|muestrame|mostrar|ver|ensename|baja|despliega) )?(?:las |mis )?notificaciones") {
            Action.Global(GlobalKind.NOTIFICATIONS)
        },
        Rule("(?:(?:abre|abrir|muestra|muestrame|mostrar) )?(?:los |las |la )?(?:ajustes|opciones|configuracion) rapid(?:os|as|a)|(?:el )?panel rapido") {
            Action.Global(GlobalKind.QUICK_SETTINGS)
        },
        Rule("(?:(?:abre|abrir|muestra|muestrame|mostrar|ver) )?(?:las )?(?:apps|aplicaciones) recientes|recientes|multitarea") {
            Action.Global(GlobalKind.RECENTS)
        },
        Rule("(?:(?:ve|ir|vete|vuelve|volver|regresa) (?:a |al )?)?(?:la )?(?:pantalla (?:de )?)?(?:inicio|principal)|(?:ve|ir|vete) a casa|escritorio") {
            Action.Global(GlobalKind.HOME)
        },
        Rule("(?:(?:ve|ir|vete|vuelve|volver|regresa|regresar) (?:hacia )?)?atras|vuelve|volver|regresa|regresar|anterior|pantalla anterior") {
            Action.Global(GlobalKind.BACK)
        },
        Rule("(?:sube|subir|sigue subiendo|desplaza(?:te)?|desliza|scroll|haz scroll)(?: (?:hacia|para))? arriba|sube|subir|mas arriba|pagina arriba|arriba|pagina anterior") {
            Action.Scroll(Direction.UP)
        },
        Rule("(?:baja|bajar|sigue bajando|desplaza(?:te)?|desliza|scroll|haz scroll|pasa)(?: (?:hacia|para))?(?: abajo)?|mas abajo|pagina abajo|abajo|siguiente pagina") {
            Action.Scroll(Direction.DOWN)
        },
        Rule("(?:gira|girar|rota|rotar|voltea|voltear)(?: la)?(?: pantalla)?|(?:pon|poner|ponlo|ponla|cambia a|modo)(?: en)? (?:horizontal|vertical)|horizontal|vertical") {
            Action.Rotate
        },
        Rule("(?:busca|buscar|buscame|encuentra|encuentrame|pon|ponme|reproduce|reproducir) (?<p>.+?) en (?<t>$T)", Targets::search),
        Rule("(?:busca|buscar|buscame) en (?<t>$T) (?<p>.+?)", Targets::search),
        Rule("(?:llevame|como llego|como ir|navega|navegar|ruta) (?:a|al|hasta|hacia) (?<p>.+?)") { Targets.searchIn(it, SearchTarget.MAPS) },
        Rule("(?:busca|buscar|buscame|googlea|googlear|investiga) (?:informacion (?:sobre|de) )?(?<p>.+?)") { Targets.searchIn(it, SearchTarget.GOOGLE) },
        Rule("(?:escribe|escribir|escribeme|teclea|teclear|introduce|introducir|dicta|redacta|pon el texto) (?<p>.+?)", ::type),
        Rule(
            "(?:toca|tocar|pulsa|pulsar|presiona|presionar|haz clic|haz click|clic|click|dale|pincha|selecciona|seleccionar|elige)" +
                "(?: (?:en|sobre|a|al|el|la|los|las))*(?: (?:boton|opcion|enlace|icono|pestana))?(?: (?:de|del|que dice))? (?<p>.+?)",
            ::tap,
        ),
        Rule(
            "(?:abre|abrir|abreme|lanza|lanzar|inicia|iniciar|ejecuta|ejecutar|arranca|entra en|entrar en|entra a|ve a|ir a|vete a|quiero usar|usa)" +
                "(?: (?:la|el))?(?: (?:app|aplicacion))?(?: (?:de|del))? (?<p>.+?)",
            Targets::open,
        ),
    )
}

internal object EnGrammar {
    private const val T =
        "youtube|you tube|google|the web|the internet|web|internet|google maps|maps|the map|the play store|play store|google play|the store|store"
    private const val FS = "full\\s*screen"

    val rules: List<Rule> = listOf(
        Rule("(?:turn|crank|pump) (?:the )?volume up|(?:turn|crank) (?:it |the volume )?up|(?:the )?volume up|(?:raise|increase) (?:the )?volume|louder|volume higher") {
            Action.Volume(Direction.UP)
        },
        Rule("turn (?:the )?volume down|turn (?:it |the volume )?down|(?:the )?volume down|(?:lower|decrease|reduce) (?:the )?volume|quieter|softer|volume lower") {
            Action.Volume(Direction.DOWN)
        },
        Rule("(?:exit|leave|close|quit|turn off|get out of|stop)(?: the)? $FS(?: mode)?|minimi[sz]e(?: (?:the|this) video)?|normal (?:screen|view)") {
            Action.Fullscreen(enter = false)
        },
        Rule("(?:(?:go|make it|switch to|turn on|enter|watch in|play in|set|put it in|view in)(?: the)? )?$FS(?: mode)?|maximi[sz]e(?: (?:the|this) video)?") {
            Action.Fullscreen(enter = true)
        },
        Rule("(?:read|tell me)(?: me)?(?: the)?(?: most)? (?:funniest|funny|hilarious|funnier) comments|(?:the )?funniest comments") {
            Action.ReadComments(CommentMode.FUNNY)
        },
        Rule("(?:read|tell me)(?: me)?(?: the)? comments (?:about|on|regarding|mentioning|that mention|talking about) (?<p>.+?)", ::topic),
        Rule("what (?:are|do|is) (?:people|they|the comments|everyone) (?:saying|say|think|thinking) (?:about|of) (?<p>.+?)", ::topic),
        Rule(
            "(?:read|tell me)(?: me)?(?: the)?(?: most)? (?:popular |top |best |liked |most liked |highest rated |top rated )?comments(?: (?:on|of|for) (?:this|the) video)?|" +
                "(?:the )?top comments|(?:open|show)(?: me)?(?: the)? comments",
        ) { Action.ReadComments(CommentMode.POPULAR) },
        Rule(
            "(?:read|describe)(?: me)?(?: (?:the|this|my|what s on the|whats on the|what is on the))? screen|read (?:this|everything|it|it out|aloud|out loud)|" +
                "(?:what s|whats|what is) on(?: the| my)? screen|what do you see|what can you see|where am i",
        ) { Action.ReadScreen },
        Rule("(?:(?:open|show|pull down|show me|check)(?: the| my)? )?notifications") { Action.Global(GlobalKind.NOTIFICATIONS) },
        Rule("(?:(?:open|show|pull down)(?: the)? )?quick settings") { Action.Global(GlobalKind.QUICK_SETTINGS) },
        Rule("(?:(?:open|show|show me)(?: the| my)? )?(?:recent apps|recents|app switcher|overview|recent applications)") {
            Action.Global(GlobalKind.RECENTS)
        },
        Rule("(?:go )?(?:to )?(?:the )?(?:home|home\\s*screen|main screen)") { Action.Global(GlobalKind.HOME) },
        Rule("(?:go )?back|previous screen|go to (?:the )?previous (?:screen|page)|return") { Action.Global(GlobalKind.BACK) },
        Rule("scroll up|page up|go up|move up|up|previous page") { Action.Scroll(Direction.UP) },
        Rule("scroll(?: down)?|page down|go down|move down|down|keep scrolling|next page|scroll more") { Action.Scroll(Direction.DOWN) },
        Rule("rotate(?: the)?(?: screen| phone)?|turn(?: the)? (?:screen|phone)(?: sideways)?|landscape(?: mode)?|portrait(?: mode)?|flip(?: the)? screen") {
            Action.Rotate
        },
        Rule("(?:search|look)(?: on| in)? (?<t>$T) for (?<p>.+?)", Targets::search),
        Rule("(?:search(?: for)?|look up|look for|find|play|watch|show me) (?<p>.+?) (?:on|in) (?<t>$T)", Targets::search),
        Rule("(?:search|look) (?:on|in) (?<t>$T) (?<p>.+?)", Targets::search),
        Rule("(?:navigate|directions|take me|get me|drive|route) (?:to|towards) (?<p>.+?)") { Targets.searchIn(it, SearchTarget.MAPS) },
        Rule("(?:search(?: for)?|google|look up|look for) (?<p>.+?)") { Targets.searchIn(it, SearchTarget.GOOGLE) },
        Rule("(?:type|write|enter|input|dictate)(?: the text)? (?<p>.+?)", ::type),
        Rule(
            "(?:tap|click|press|hit|select|choose|touch|push)(?: (?:on|the))*(?: (?:button|option|link|icon|tab))?(?: (?:for|called|named|labeled|labelled|that says))? (?<p>.+?)",
            ::tap,
        ),
        Rule("(?:open|launch|start|run|go to|switch to|bring up|fire up|use)(?: (?:the|my))?(?: (?:app|application))? (?<p>.+?)", Targets::open),
    )
}

internal object NlGrammar {
    private const val T =
        "youtube|google|internet|het internet|het web|web|google maps|maps|kaarten|de kaart|google kaarten|" +
            "de play store|play store|google play|de winkel|winkel"
    private const val C = "reacties|reactie|opmerkingen|comments"
    private const val FS = "(?:volledig|volledige|beeldvullend|schermvullend|full\\s*screen)(?: scherm)?"

    val rules: List<Rule> = listOf(
        Rule("(?:zet )?(?:het )?(?:volume|geluid) (?:omhoog|hoger|harder|luider)|(?:zet )?(?:het )?(?:volume |geluid )?harder|luider|meer volume") {
            Action.Volume(Direction.UP)
        },
        Rule("(?:zet )?(?:het )?(?:volume|geluid) (?:omlaag|lager|zachter)|(?:zet )?(?:het )?(?:volume |geluid )?zachter|stiller|minder volume") {
            Action.Volume(Direction.DOWN)
        },
        Rule("(?:verlaat|sluit|stop|beeindig)(?: het| de)? $FS(?: modus)?|$FS(?: modus)? (?:uit|verlaten|sluiten|uitzetten)|normaal scherm") {
            Action.Fullscreen(enter = false)
        },
        Rule("(?:(?:zet|ga|maak|kijk|bekijk|speel)(?: het| de video)?(?: naar| op| in)? )?$FS(?: modus)?(?: aan)?|maximaliseer(?: de video)?") {
            Action.Fullscreen(enter = true)
        },
        Rule("(?:lees|vertel)(?: me)?(?: de)?(?: meest)? (?:grappigste|leukste|grappige|lolligste) (?:$C)(?: voor)?") {
            Action.ReadComments(CommentMode.FUNNY)
        },
        Rule("(?:lees|vertel)(?: me)?(?: de)? (?:$C) (?:over|met|waarin|die gaan over) (?<p>.+?)(?: voor)?", ::topic),
        Rule("wat zeggen (?:de )?(?:mensen|reacties|ze|kijkers) over (?<p>.+?)", ::topic),
        Rule(
            "(?:lees|vertel)(?: me)?(?: de)?(?: meest)? (?:(?:populairste|populaire|beste|top) )?(?:$C)(?: voor)?|" +
                "(?:open|toon|laat)(?: me)?(?: de)? (?:$C)(?: zien)?|(?:de )?beste (?:$C)",
        ) { Action.ReadComments(CommentMode.POPULAR) },
        Rule(
            "lees(?: het| dit| mijn)? scherm(?: voor)?|lees (?:dit|alles|het|het scherm|wat er staat) voor|lees voor|" +
                "wat staat er op(?: het| mijn)? scherm|wat zie je|beschrijf(?: het)? scherm|waar ben ik",
        ) { Action.ReadScreen },
        Rule("(?:(?:open|toon|laat)(?: de| mijn)? )?(?:meldingen|notificaties)(?: zien)?") { Action.Global(GlobalKind.NOTIFICATIONS) },
        Rule("(?:(?:open|toon)(?: de)? )?(?:snelle instellingen|snelinstellingen|snel instellingen)") { Action.Global(GlobalKind.QUICK_SETTINGS) },
        Rule("(?:(?:open|toon)(?: de)? )?(?:recente apps|recente applicaties|recente|recent|overzicht|app overzicht)") {
            Action.Global(GlobalKind.RECENTS)
        },
        Rule("(?:ga )?(?:naar )?(?:het )?(?:startscherm|beginscherm|home|thuisscherm|hoofdscherm)") { Action.Global(GlobalKind.HOME) },
        Rule("(?:ga )?terug|vorige(?: scherm)?|ga naar (?:het )?vorige scherm") { Action.Global(GlobalKind.BACK) },
        Rule("(?:scroll|scrol)(?: naar)? (?:boven|omhoog)|naar boven|omhoog|hoger|vorige pagina") { Action.Scroll(Direction.UP) },
        Rule("(?:scroll|scrol)(?: naar)?(?: beneden| omlaag)?|naar beneden|omlaag|verder(?: scrollen)?|volgende pagina|lager") {
            Action.Scroll(Direction.DOWN)
        },
        Rule("(?:draai|roteer|kantel)(?: het| de)?(?: scherm| telefoon)?|liggend(?: scherm)?|staand(?: scherm)?") { Action.Rotate },
        Rule("(?:zoek(?: naar)?|speel|bekijk|kijk|vind) (?<p>.+?) (?:op|in) (?<t>$T)", Targets::search),
        Rule("zoek (?:op|in) (?<t>$T) (?:naar )?(?<p>.+?)", Targets::search),
        Rule("(?:navigeer|route|breng me|rijd) naar (?<p>.+?)") { Targets.searchIn(it, SearchTarget.MAPS) },
        Rule("(?:zoek(?: naar| op)?|google) (?<p>.+?)") { Targets.searchIn(it, SearchTarget.GOOGLE) },
        Rule("(?:typ|type|schrijf|dicteer)(?: de tekst)? (?<p>.+?)", ::type),
        Rule("voer (?<p>.+?) in", ::type),
        Rule(
            "(?:tik|klik|druk|tap|selecteer|kies|raak)(?: (?:op|de|het))*(?: (?:knop|optie|link|icoon|tabblad))?(?: (?:met|genaamd))? (?<p>.+?)(?: aan)?",
            ::tap,
        ),
        Rule("(?:open|start|ga naar|lanceer|gebruik)(?: (?:de|het|mijn))?(?: (?:app|applicatie))? (?<p>.+?)", Targets::open),
    )
}
