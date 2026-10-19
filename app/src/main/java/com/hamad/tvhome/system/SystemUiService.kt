package com.hamad.tvhome.system

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/** Control Center + App Switcher overlays. STUB — owned by the system agent. */
class SystemUiService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit
}
