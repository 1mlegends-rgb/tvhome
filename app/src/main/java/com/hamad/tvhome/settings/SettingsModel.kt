package com.hamad.tvhome.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import com.hamad.tvhome.AppEntry
import com.hamad.tvhome.AppRepository
import com.hamad.tvhome.R
import com.hamad.tvhome.prefs.LauncherPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** A screen in the Settings hierarchy. [key] identifies it for focus memory. */
internal sealed class Dest(val key: String) {
    data object Root : Dest("root")
    data object General : Dest("general")
    data object About : Dest("about")
    data object Remotes : Dest("remotes")
    data object Apps : Dest("apps")
    data class App(val pkg: String) : Dest("app:$pkg")
    data object VideoAudio : Dest("video")
    data object ScreenSaver : Dest("screensaver")
    data object ScreenSaverType : Dest("screensaver.type")
    data object StartAfter : Dest("screensaver.start")
    data object Network : Dest("network")
    data object HomeScreen : Dest("home")
    data object Region : Dest("home.region")
    data object System : Dest("system")

    companion object {
        private val fixed by lazy {
            listOf(
                Root, General, About, Remotes, Apps, VideoAudio, ScreenSaver, ScreenSaverType,
                StartAfter, Network, HomeScreen, Region, System,
            ).associateBy { it.key }
        }

        fun fromKey(key: String): Dest? =
            if (key.startsWith("app:")) App(key.removePrefix("app:")) else fixed[key]
    }
}

/** The big artwork on the left half of every page. */
internal sealed interface Art {
    data object Gear : Art
    data object Device : Art
    data object Remote : Art
    data object AppGrid : Art
    data object Display : Art
    data object Landscape : Art
    data object Wifi : Art
    data object HomeGrid : Art
    data class Tile(val image: ImageBitmap) : Art
}

internal sealed interface Entry {
    val id: String
}

internal data class Header(override val id: String, val text: String) : Entry

/** A paragraph in the list (not focusable). */
internal data class Note(override val id: String, val text: String) : Entry

/**
 * A row. [onClick] null = informational row (still focusable, like tvOS's About rows).
 * [checked] non-null turns the row into a picker option with a leading checkmark slot.
 * [footer] is shown under the artwork while the row has focus.
 */
internal data class Item(
    override val id: String,
    val title: String,
    val value: String? = null,
    val chevron: Boolean = false,
    val checked: Boolean? = null,
    val icon: ImageBitmap? = null,
    val footer: String? = null,
    val onClick: (() -> Unit)? = null,
) : Entry

internal data class PageSpec(val title: String, val art: Art, val entries: List<Entry>, val caption: String? = null)

internal data class AlertButton(val label: String, val destructive: Boolean = false, val action: () -> Unit = {})

internal data class AlertSpec(val title: String, val message: String?, val buttons: List<AlertButton>, val focusIndex: Int = 0)

internal data class FocusMemo(val id: String, val index: Int)

/**
 * Navigation stack, alert, and the bridge to the platform. One per activity.
 * [tick] bumps on every resume so pages re-read system values the user may have changed
 * in Android's own settings screens.
 */
@Stable
internal class SettingsController(val context: Context, private val scope: CoroutineScope) {
    val info = SystemInfo(context)
    val prefs = LauncherPrefs.get(context)
    private val pm: PackageManager = context.packageManager

    val stack = mutableStateListOf<Dest>(Dest.Root)
    var forward by mutableStateOf(true)
        private set
    var alert by mutableStateOf<AlertSpec?>(null)
    var tick by mutableIntStateOf(0)
        private set
    var apps by mutableStateOf<List<AppEntry>?>(null)
        private set

    val focusMemory = HashMap<String, FocusMemo>()
    private var appsJob: Job? = null

    fun push(dest: Dest) {
        forward = true
        stack.add(dest)
    }

    /** Returns false at the root. */
    fun pop(): Boolean {
        if (stack.size <= 1) return false
        forward = false
        stack.removeAt(stack.lastIndex)
        return true
    }

    /** Deep link, e.g. "screensaver" or "general,about": replaces the stack below the root. */
    fun openPath(path: String) {
        val dests = path.split(',').mapNotNull { Dest.fromKey(it.trim()) }.filter { it != Dest.Root }
        if (dests.isEmpty()) return
        if (dests.any { it == Dest.Apps || it is Dest.App }) loadApps()
        forward = true
        stack.removeRange(1, stack.size)
        stack.addAll(dests)
    }

    fun onResume() {
        tick++
        if (apps != null) loadApps()
        val top = stack.lastOrNull()
        if (top is Dest.App && !info.isInstalled(top.pkg)) pop()
    }

    fun loadApps() {
        if (appsJob?.isActive == true) return
        appsJob = scope.launch {
            val loaded = withContext(Dispatchers.Default) {
                runCatching { AppRepository(context).load() }
                    .onFailure { Log.w(TAG, "App list failed", it) }
                    .getOrDefault(emptyList())
            }
            apps = loaded.filterNot { it.isPseudo }.sortedBy { it.label.lowercase() }
        }
    }

    /** String resource, formatted with [Locale.US] so numbers stay Western on the Arabic box. */
    fun s(@StringRes id: Int, vararg args: Any): String =
        if (args.isEmpty()) context.getString(id) else String.format(Locale.US, context.getString(id), *args)

    fun onOff(on: Boolean): String = s(if (on) R.string.settings_on else R.string.settings_off)

    /** True when [intent] resolves to an activity we're allowed to start. */
    fun resolves(intent: Intent): Boolean {
        val info = pm.resolveActivity(intent, 0)?.activityInfo ?: return false
        return info.exported || info.packageName == context.packageName
    }

    fun resolverClass(intent: Intent): String? = pm.resolveActivity(intent, 0)?.activityInfo?.name

    fun firstResolvable(vararg intents: Intent): Intent? = intents.firstOrNull(::resolves)

    fun open(intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "Cannot start $intent", e)
            Toast.makeText(context, R.string.settings_cant_open, Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            Log.w(TAG, "Not allowed to start $intent", e)
            Toast.makeText(context, R.string.settings_cant_open, Toast.LENGTH_SHORT).show()
        }
    }

    fun refresh() {
        tick++
    }

    private companion object {
        const val TAG = "TvSettings"
    }
}
