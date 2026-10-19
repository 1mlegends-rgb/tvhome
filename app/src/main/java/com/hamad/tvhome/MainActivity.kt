package com.hamad.tvhome

import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.hamad.tvhome.home.ClickSound
import com.hamad.tvhome.home.ShelfImages
import com.hamad.tvhome.prefs.LauncherPrefs
import com.hamad.tvhome.ui.HomeScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: HomeViewModel by viewModels()
    private var click: ClickSound? = null

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = vm.reload()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ShelfImages.install()
        click = ClickSound(this)
        registerReceiver(
            packageReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addDataScheme("package")
            },
        )
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = vm.onBack()
        })
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.intents.collect(::start)
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                val prefs = LauncherPrefs.get(this@MainActivity)
                vm.ticks.collect { if (prefs.state.value.clickSounds) click?.play() }
            }
        }
        // tvOS's home layout is drawn left-to-right whatever the box's locale is.
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) { HomeScreen(vm) }
        }
        // The hero backdrop paints every pixel itself; skipping the window's black fill saves a
        // full-screen layer per frame on low-end TV GPUs.
        window.setBackgroundDrawable(null)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) vm.onHome()
    }

    override fun onResume() {
        super.onResume()
        vm.onResume()
    }

    override fun onPause() {
        vm.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        unregisterReceiver(packageReceiver)
        click?.release()
        click = null
        super.onDestroy()
    }

    private fun start(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.w("TvHome", "Cannot start $intent", e)
            Toast.makeText(this, "This app can't be opened.", Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            Log.w("TvHome", "Not allowed to start $intent", e)
            Toast.makeText(this, "This app can't be opened.", Toast.LENGTH_SHORT).show()
        }
    }
}
