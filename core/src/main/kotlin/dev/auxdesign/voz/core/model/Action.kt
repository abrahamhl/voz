package dev.auxdesign.voz.core.model

/**
 * The fixed action vocabulary. Anything VOZ does on the phone is one of these.
 * [Verb.wire] names are shared with the cloud planner JSON schema.
 */
enum class Verb(val wire: String, val cloudAllowed: Boolean = true) {
    OPEN_APP("open_app"),
    SEARCH("search"),
    BACK("back"),
    HOME("home"),
    RECENTS("recents"),
    NOTIFICATIONS("notifications"),
    QUICK_SETTINGS("quick_settings"),
    SCROLL_UP("scroll_up"),
    SCROLL_DOWN("scroll_down"),
    TAP("tap"),
    TYPE("type"),
    READ_SCREEN("read_screen"),
    VOLUME_UP("volume_up"),
    VOLUME_DOWN("volume_down"),
    ROTATE("rotate"),
    STOP("stop", cloudAllowed = false),
    FULLSCREEN("fullscreen"),
    EXIT_FULLSCREEN("exit_fullscreen"),
    READ_COMMENTS("read_comments");

    companion object {
        fun fromWire(s: String?): Verb? = entries.firstOrNull { it.wire == s }
        val cloudVocabulary: List<String> get() = entries.filter { it.cloudAllowed }.map { it.wire }
    }
}

enum class SearchTarget(val wire: String) {
    YOUTUBE("youtube"),
    GOOGLE("google"),
    MAPS("maps"),
    PLAY("play");

    companion object {
        fun fromWire(s: String?): SearchTarget? = entries.firstOrNull { it.wire == s }

        /** Apps whose name can be followed by a query ("YouTube cats"). */
        fun forPackage(pkg: String): SearchTarget? = when (pkg) {
            "com.google.android.youtube" -> YOUTUBE
            "com.google.android.apps.maps" -> MAPS
            "com.android.vending" -> PLAY
            "com.google.android.googlequicksearchbox" -> GOOGLE
            else -> null
        }
    }
}

enum class GlobalKind { BACK, HOME, RECENTS, NOTIFICATIONS, QUICK_SETTINGS }

enum class Direction { UP, DOWN }

enum class CommentMode(val wire: String) {
    POPULAR("popular"),
    FUNNY("funny"),
    TOPIC("topic");

    companion object {
        fun fromWire(s: String?): CommentMode? = entries.firstOrNull { it.wire == s }
    }
}

sealed interface Action {
    val verb: Verb

    data class OpenApp(val app: String) : Action {
        override val verb: Verb get() = Verb.OPEN_APP
    }

    data class Search(val query: String, val target: SearchTarget) : Action {
        override val verb: Verb get() = Verb.SEARCH
    }

    data class Global(val kind: GlobalKind) : Action {
        override val verb: Verb
            get() = when (kind) {
                GlobalKind.BACK -> Verb.BACK
                GlobalKind.HOME -> Verb.HOME
                GlobalKind.RECENTS -> Verb.RECENTS
                GlobalKind.NOTIFICATIONS -> Verb.NOTIFICATIONS
                GlobalKind.QUICK_SETTINGS -> Verb.QUICK_SETTINGS
            }
    }

    data class Scroll(val direction: Direction) : Action {
        override val verb: Verb get() = if (direction == Direction.UP) Verb.SCROLL_UP else Verb.SCROLL_DOWN
    }

    data class Tap(val label: String) : Action {
        override val verb: Verb get() = Verb.TAP
    }

    data class Type(val text: String) : Action {
        override val verb: Verb get() = Verb.TYPE
    }

    data object ReadScreen : Action {
        override val verb: Verb get() = Verb.READ_SCREEN
    }

    data class Volume(val direction: Direction) : Action {
        override val verb: Verb get() = if (direction == Direction.UP) Verb.VOLUME_UP else Verb.VOLUME_DOWN
    }

    data object Rotate : Action {
        override val verb: Verb get() = Verb.ROTATE
    }

    data object Stop : Action {
        override val verb: Verb get() = Verb.STOP
    }

    data class Fullscreen(val enter: Boolean) : Action {
        override val verb: Verb get() = if (enter) Verb.FULLSCREEN else Verb.EXIT_FULLSCREEN
    }

    data class ReadComments(
        val mode: CommentMode,
        val topic: String? = null,
        val count: Int = DEFAULT_COUNT,
    ) : Action {
        override val verb: Verb get() = Verb.READ_COMMENTS

        companion object {
            const val DEFAULT_COUNT = 3
        }
    }
}
