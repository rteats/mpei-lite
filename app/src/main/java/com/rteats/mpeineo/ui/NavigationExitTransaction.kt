package com.rteats.mpeineo.ui

/**
 * Navigation state is committed exactly once after a slide-back completes,
 * or immediately when that slide is interrupted by another destination.
 * This is separate from frame interpolation and does not own any Compose UI.
 */
internal class NavigationExitTransaction {
    private var completion: (() -> Unit)? = null

    val pending: Boolean get() = completion != null

    fun begin(onExit: () -> Unit) {
        check(!pending) { "A service back transaction is already pending" }
        completion = onExit
    }

    fun settle(): Boolean {
        val task = completion ?: return false
        completion = null
        task()
        return true
    }
}
