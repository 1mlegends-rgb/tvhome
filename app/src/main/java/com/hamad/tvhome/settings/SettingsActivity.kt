package com.hamad.tvhome.settings

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.lifecycleScope

/** tvOS Settings: artwork on the left, a focus-driven list on the right, pages pushed and popped with BACK. */
class SettingsActivity : ComponentActivity() {
    private lateinit var controller: SettingsController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        controller = SettingsController(this, lifecycleScope)
        intent?.getStringExtra(EXTRA_PAGE)?.let(controller::openPath)
        // tvOS layout is left-to-right whatever the box's locale (the box runs Arabic).
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                SettingsScreen(controller, onExit = ::finish)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_PAGE)?.let(controller::openPath)
    }

    override fun onResume() {
        super.onResume()
        // Values may have changed in Android's own settings screens; re-read them.
        controller.onResume()
    }

    companion object {
        /** Optional page path to open, e.g. "screensaver" or "general,about" (see [Dest] keys). */
        const val EXTRA_PAGE = "com.hamad.tvhome.settings.PAGE"
    }
}
