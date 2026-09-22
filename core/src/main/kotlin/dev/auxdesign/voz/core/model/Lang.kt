package dev.auxdesign.voz.core.model

/** Languages VOZ understands and speaks. */
enum class Lang(val tag: String) {
    ES("es-ES"),
    EN("en-US"),
    NL("nl-NL");

    val code: String get() = tag.substringBefore('-')

    companion object {
        val DEFAULT = EN

        /** Resolves a BCP-47 tag or bare language code ("es", "nl-BE") to a supported language. */
        fun fromTag(tag: String?): Lang? {
            if (tag.isNullOrBlank()) return null
            val code = tag.trim().replace('_', '-').substringBefore('-').lowercase()
            return entries.firstOrNull { it.code == code }
        }

        fun fromTagOrDefault(tag: String?): Lang = fromTag(tag) ?: DEFAULT
    }
}
