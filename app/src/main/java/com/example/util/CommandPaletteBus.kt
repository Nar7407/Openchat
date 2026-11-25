package com.example.util

import android.view.KeyEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Lightweight event bus for global hardware keyboard shortcuts (Ctrl+K / Meta+K)
 * to summon the Quick-Switch Command Palette instantaneously from anywhere in AllChat.
 */
object CommandPaletteBus {
    private val _openEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val openEvents: SharedFlow<Unit> = _openEvents.asSharedFlow()

    fun handleKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            if (event.keyCode == KeyEvent.KEYCODE_K && (event.isCtrlPressed || event.isMetaPressed)) {
                _openEvents.tryEmit(Unit)
                return true
            }
        }
        return false
    }

    fun triggerOpen() {
        _openEvents.tryEmit(Unit)
    }
}
