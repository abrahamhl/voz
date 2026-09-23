package dev.auxdesign.voz.a11y

/** Forcing an orientation without losing the user's own auto-rotate choice (pure, unit-tested). */
object RotationPlan {
    /** [savedAutoRotate] = what VOZ must give back later (null = nothing saved). */
    data class Change(val autoRotate: Int, val savedAutoRotate: Int?)

    /**
     * Landscape: auto-rotate off (or the phone would turn back), remembering the user's value the first time.
     * Portrait: auto-rotate back to what the user had, and forget it.
     */
    fun to(landscape: Boolean, autoRotate: Int, savedAutoRotate: Int?): Change =
        if (landscape) {
            Change(autoRotate = 0, savedAutoRotate = savedAutoRotate ?: autoRotate)
        } else {
            Change(autoRotate = savedAutoRotate ?: autoRotate, savedAutoRotate = null)
        }
}
