package com.bluvo.home

import android.accessibilityservice.AccessibilityService
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/** Listens to remote keys everywhere and opens the app mapped to a button. */
class KeyMapService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val pkg = KeyMap.get(this, event.keyCode) ?: return false
        if (event.action == KeyEvent.ACTION_UP) KeyMap.launch(this, pkg)
        return true
    }
}
