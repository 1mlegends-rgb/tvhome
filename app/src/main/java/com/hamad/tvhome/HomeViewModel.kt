package com.hamad.tvhome

import android.app.Application
import android.app.SearchManager
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hamad.tvhome.home.Bar
import com.hamad.tvhome.home.ContentRepository
import com.hamad.tvhome.home.HeroItem
import com.hamad.tvhome.home.Providers
import com.hamad.tvhome.home.ShelfCursor
import com.hamad.tvhome.home.ShelfNav
import com.hamad.tvhome.home.Tab
import com.hamad.tvhome.home.TabPage
import com.hamad.tvhome.home.Title
import com.hamad.tvhome.home.Zone
import com.hamad.tvhome.prefs.LauncherPrefs
import com.hamad.tvhome.prefs.LauncherSettings
import com.hamad.tvhome.search.SearchActivity
import com.hamad.tvhome.settings.SettingsActivity
import com.hamad.tvhome.tmdb.TmdbClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SheetAction(val label: String, val destructive: Boolean = false) {
    Move("Move"),
    Hide("Hide App"),
    Info("App Info"),
    Uninstall("Uninstall", destructive = true),
    Cancel("Cancel"),
}

data class Sheet(val app: AppEntry, val actions: List<SheetAction>, val focus: Int = 0)

data class Nudge(val dir: Dir, val id: Long)

/**
 * Home: a floating tab bar (Search · Home · Movies · TV Shows · Apps · Settings) over per-tab
 * pages. All focus lives here as fine-grained snapshot state (see [TabPage]) so the UI only
 * recomposes the pieces a key press actually changes.
 */
class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = AppRepository(app)
    private val order = OrderStore(app)
    private val prefs = LauncherPrefs.get(app)
    private val tmdb = TmdbClient.get(app)
    private val content = ContentRepository(tmdb)
    private val _intents = MutableSharedFlow<Intent>(extraBufferCapacity = 4)
    val intents: SharedFlow<Intent> = _intents
    private val _ticks = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** One event per successful focus move, for the click sound. */
    val ticks: SharedFlow<Unit> = _ticks

    val pages: Map<Tab, TabPage> = Tab.entries.associateWith(::TabPage)
    var tab by mutableStateOf(Tab.Home)
        private set
    var barSlot by mutableIntStateOf(Bar.slotOf(Tab.Home))
        private set
    var apps by mutableStateOf<List<AppEntry>>(emptyList())
        private set
    var editing by mutableStateOf(false)
        private set
    var sheet by mutableStateOf<Sheet?>(null)
        private set
    var pressedKey by mutableStateOf<String?>(null)
        private set
    var nudge by mutableStateOf<Nudge?>(null)
        private set
    var resumed by mutableStateOf(true)
        private set
    var autoplay by mutableStateOf(true)
        private set
    /** Bumped on every key press; the UI's inactivity dim restarts from it. */
    var inputSeq by mutableIntStateOf(0)
        private set
    /** Direction of the last D-pad move (not observed): the newly focused card sways toward it. */
    var lastDir: Dir = Dir.Right
        private set

    val page: TabPage get() = pages.getValue(tab)

    /** Every installed app in the user's order, hidden ones included (they keep their place). */
    private var allApps: List<AppEntry> = emptyList()
    private var settings: LauncherSettings = prefs.state.value
    private var region: String = settings.contentRegion
    private val loads = HashMap<Tab, Job>()
    private var nudgeSeq = 0L

    init {
        autoplay = settings.topShelfAutoplay
        pages.getValue(Tab.Home).row = Zone.HERO
        reload()
        viewModelScope.launch { prefs.state.collect(::applySettings) }
    }

    fun reload() {
        viewModelScope.launch {
            allApps = withContext(Dispatchers.Default) { order.arrange(repo.load()) }
            publishApps()
            ensureContent(Tab.Home)
            delay(PREFETCH_DELAY_MS)
            ensureContent(Tab.Movies)
            ensureContent(Tab.Shows)
        }
    }

    fun onResume() {
        resumed = true
        applySettings(prefs.state.value)
        // Offline at boot: try again whenever Home comes back.
        if (allApps.isNotEmpty()) Tab.entries.forEach { if (isEmpty(pages.getValue(it))) ensureContent(it) }
    }

    fun onPause() {
        resumed = false
    }

    // ── Settings & content ───────────────────────────────────────────────────────────────────

    private fun applySettings(next: LauncherSettings) {
        val prev = settings
        settings = next
        autoplay = next.topShelfAutoplay
        if (next.hiddenApps != prev.hiddenApps) publishApps()
        if (next.contentRegion != region) {
            region = next.contentRegion
            loads.values.forEach { it.cancel() }
            loads.clear()
            Tab.entries.forEach { ensureContent(it, force = true) }
        }
    }

    /** Re-derives the visible apps from [allApps] minus hidden ones, keeping grid focus on the same app. */
    private fun publishApps() {
        val grid = pages.getValue(Tab.Apps)
        val focusKey = apps.getOrNull(grid.gridFocus)?.key
        val visible = allApps.filter { it.key !in settings.hiddenApps }
        val keyIndex = visible.indexOfFirst { it.key == focusKey }
        apps = visible
        grid.gridFocus = if (keyIndex >= 0) keyIndex else grid.gridFocus.coerceIn(0, (visible.size - 1).coerceAtLeast(0))
        editing = editing && visible.isNotEmpty()
        sheet = null
        pages.values.forEach(::settleFocus)
    }

    private fun isEmpty(p: TabPage) = p.tab != Tab.Apps && p.hero.isEmpty() && p.rows.isEmpty()

    private fun ensureContent(tab: Tab, force: Boolean = false) {
        if (tab == Tab.Apps) return
        val p = pages.getValue(tab)
        if (loads[tab]?.isActive == true) return
        if (!force && !isEmpty(p)) return
        val reg = region
        val installed = allApps.mapTo(HashSet()) { it.packageName }
        if (isEmpty(p)) {
            p.heroLoading = true
            p.rowsLoading = true
        }
        loads[tab] = viewModelScope.launch {
            val hero = content.hero(tab, reg, installed)
            if (reg != region) return@launch
            p.hero = hero
            p.heroPage = p.heroPage.coerceIn(0, (hero.size - 1).coerceAtLeast(0))
            p.heroLoading = false
            settleFocus(p)
            val rows = content.rows(tab, reg, installed)
            if (reg != region) return@launch
            p.rows = rows
            p.rowsLoading = false
            settleFocus(p)
        }
    }

    /** Keeps a page's focus on something that exists after its content or the app list changed. */
    private fun settleFocus(p: TabPage) {
        if (p.tab == Tab.Apps) {
            if (apps.isEmpty() && p.row >= 0) p.row = Zone.BAR
            return
        }
        val rows = rowCount(p)
        when {
            p.row == Zone.HERO && !p.hasHero -> p.row = if (rows > 0) 0 else Zone.BAR
            p.row >= rows -> p.row = when {
                rows > 0 -> rows - 1
                p.hasHero -> Zone.HERO
                else -> Zone.BAR
            }
        }
    }

    // ── Rows of a page (the UI lays them out in this same order) ─────────────────────────────

    fun showsAppsRow(p: TabPage): Boolean = p.tab == Tab.Home && apps.isNotEmpty()

    private fun rowCount(p: TabPage): Int = (if (showsAppsRow(p)) 1 else 0) + p.rows.size

    private fun rowId(p: TabPage, row: Int): String =
        if (showsAppsRow(p)) {
            if (row == 0) APPS_ROW else p.rows[row - 1].id
        } else {
            p.rows[row].id
        }

    private fun rowSize(p: TabPage, row: Int): Int =
        if (showsAppsRow(p)) {
            if (row == 0) apps.size else p.rows[row - 1].items.size
        } else {
            p.rows[row].items.size
        }

    private fun titleAt(p: TabPage, row: Int, col: Int): Title? {
        val content = if (showsAppsRow(p)) p.rows.getOrNull(row - 1) else p.rows.getOrNull(row)
        return content?.items?.getOrNull(col)
    }

    // ── Input ────────────────────────────────────────────────────────────────────────────────

    fun onDirection(dir: Dir) {
        val p = page
        inputSeq++
        lastDir = dir
        when {
            sheet != null -> moveSheet(dir)
            editing -> moveApp(dir)
            p.row == Zone.BAR -> moveInBar(p, dir)
            p.tab == Tab.Apps -> moveInGrid(p, dir)
            p.row == Zone.HERO -> moveInHero(p, dir)
            else -> moveInRows(p, dir)
        }
    }

    fun onSelect() {
        val p = page
        inputSeq++
        val s = sheet
        when {
            s != null -> runSheetAction(s.actions[s.focus], s.app)
            editing -> finishEditing()
            p.row == Zone.BAR -> selectInBar(p)
            p.tab == Tab.Apps -> apps.getOrNull(p.gridFocus)?.let { launch(it.key, it.launchIntent) }
            p.row == Zone.HERO -> selectHero(p)
            else -> selectInRows(p)
        }
    }

    fun onLongSelect() {
        val p = page
        inputSeq++
        when {
            sheet != null -> Unit
            editing -> openSheet(apps.getOrNull(p.gridFocus), fromEditing = true)
            p.tab == Tab.Apps && p.row >= 0 && apps.isNotEmpty() -> editing = true
            else -> focusedApp(p)?.let(::startMoving)
        }
    }

    fun onMenu() {
        val p = page
        inputSeq++
        when {
            sheet != null -> sheet = null
            p.tab == Tab.Apps && p.row >= 0 -> openSheet(apps.getOrNull(p.gridFocus), fromEditing = editing)
            else -> openSheet(focusedApp(p), fromEditing = false)
        }
    }

    /** Back unwinds one level and never leaves Home: content → tab bar → Home tab. */
    fun onBack() {
        val p = page
        inputSeq++
        when {
            sheet != null -> sheet = null
            editing -> finishEditing()
            p.row != Zone.BAR -> {
                p.row = Zone.BAR
                p.autoAdvances = 0
                barSlot = Bar.slotOf(tab)
                tick()
            }
            barSlot != Bar.slotOf(tab) -> {
                barSlot = Bar.slotOf(tab)
                tick()
            }
            tab != Tab.Home -> {
                switchTab(Tab.Home)
                barSlot = Bar.slotOf(Tab.Home)
                tick()
            }
        }
    }

    /** Home pressed while already home: the Home tab, scrolled to the top, everything dismissed. */
    fun onHome() {
        inputSeq++
        if (editing) persistOrder(apps)
        editing = false
        sheet = null
        pressedKey = null
        switchTab(Tab.Home)
        barSlot = Bar.slotOf(Tab.Home)
        val home = page
        home.heroButton = ShelfNav.PLAY
        home.autoAdvances = 0
        home.row = when {
            home.hasHero -> Zone.HERO
            rowCount(home) > 0 -> 0
            else -> Zone.BAR
        }
    }

    /** Autoplay: one page per call; stops after one full loop so an idle screen goes still. */
    fun autoAdvance() {
        val p = page
        if (p.hero.size < 2) return
        p.heroPage = (p.heroPage + 1) % p.hero.size
        p.autoAdvances += 1
    }

    // ── Movement ─────────────────────────────────────────────────────────────────────────────

    private fun moveInBar(p: TabPage, dir: Dir) {
        when (dir) {
            Dir.Left, Dir.Right -> {
                val next = barSlot + dir.dx
                if (next !in 0 until Bar.COUNT) return nudge(dir)
                barSlot = next
                Bar.tabAt(next)?.let(::switchTab)
                tick()
            }
            Dir.Down -> enterContent(p)
            Dir.Up -> nudge(dir)
        }
    }

    private fun switchTab(next: Tab) {
        if (next == tab) return
        tab = next
        val p = pages.getValue(next)
        p.row = Zone.BAR
        p.autoAdvances = 0
        ensureContent(next)
    }

    private fun enterContent(p: TabPage) {
        val target = when {
            p.tab == Tab.Apps -> if (apps.isNotEmpty()) 0 else null
            p.hasHero -> Zone.HERO
            rowCount(p) > 0 -> 0
            else -> null
        } ?: return nudge(Dir.Down)
        if (p.tab == Tab.Apps) p.gridFocus = p.gridFocus.coerceIn(0, apps.size - 1)
        p.row = target
        barSlot = Bar.slotOf(p.tab)
        tick()
    }

    private fun leaveToBar(p: TabPage) {
        p.row = Zone.BAR
        barSlot = Bar.slotOf(p.tab)
        tick()
    }

    private fun moveInGrid(p: TabPage, dir: Dir) {
        if (dir == Dir.Up && p.gridFocus < GridNav.COLUMNS) return leaveToBar(p)
        val next = GridNav.move(p.gridFocus, apps.size, dir) ?: return nudge(dir)
        p.gridFocus = next
        tick()
    }

    private fun moveInHero(p: TabPage, dir: Dir) {
        when (dir) {
            Dir.Up -> leaveToBar(p)
            Dir.Down -> if (rowCount(p) > 0) focusRow(p, 0) else nudge(dir)
            Dir.Left, Dir.Right -> {
                if (p.hero.isEmpty()) return nudge(dir)
                val next = ShelfNav.move(ShelfCursor(p.heroPage, p.heroButton), p.hero.size, dir)
                    ?: return nudge(dir)
                // Paging by hand ends autoplay until focus leaves the hero (HIG: "until focused").
                if (next.page != p.heroPage) p.autoAdvances = p.hero.size
                p.heroPage = next.page
                p.heroButton = next.button
                tick()
            }
        }
    }

    private fun moveInRows(p: TabPage, dir: Dir) {
        val r = p.row
        when (dir) {
            Dir.Left, Dir.Right -> {
                val col = p.col(rowId(p, r))
                val next = col.intValue.coerceAtMost(rowSize(p, r) - 1) + dir.dx
                if (next !in 0 until rowSize(p, r)) return nudge(dir)
                col.intValue = next
                tick()
            }
            Dir.Up -> when {
                r > 0 -> focusRow(p, r - 1)
                p.hasHero -> {
                    p.row = Zone.HERO
                    p.heroButton = ShelfNav.PLAY
                    p.autoAdvances = 0
                    tick()
                }
                else -> leaveToBar(p)
            }
            Dir.Down -> if (r + 1 < rowCount(p)) focusRow(p, r + 1) else nudge(dir)
        }
    }

    private fun focusRow(p: TabPage, row: Int) {
        val col = p.col(rowId(p, row))
        col.intValue = col.intValue.coerceIn(0, (rowSize(p, row) - 1).coerceAtLeast(0))
        p.row = row
        tick()
    }

    private fun moveApp(dir: Dir) {
        val p = pages.getValue(Tab.Apps)
        val target = GridNav.move(p.gridFocus, apps.size, dir) ?: return nudge(dir)
        apps = GridNav.reorder(apps, p.gridFocus, target)
        p.gridFocus = target
        tick()
    }

    private fun moveSheet(dir: Dir) {
        val s = sheet ?: return
        val next = s.focus + dir.dy
        if (dir.dy != 0 && next in s.actions.indices) {
            sheet = s.copy(focus = next)
            tick()
        }
    }

    private fun tick() {
        _ticks.tryEmit(Unit)
    }

    private fun nudge(dir: Dir) {
        nudge = Nudge(dir, ++nudgeSeq)
    }

    // ── Selection ────────────────────────────────────────────────────────────────────────────

    private fun selectInBar(p: TabPage) {
        val app = getApplication<Application>()
        when (barSlot) {
            Bar.SEARCH -> launch(barKey(Bar.SEARCH), Intent(app, SearchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            Bar.SETTINGS -> launch(barKey(Bar.SETTINGS), Intent(app, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            else -> enterContent(p)
        }
    }

    private fun selectHero(p: TabPage) {
        val item = p.heroItem ?: return
        if (p.heroButton == ShelfNav.MORE_INFO) launch(KEY_MORE_INFO, productPage(item.title)) else play(item)
    }

    private fun selectInRows(p: TabPage) {
        val r = p.row
        val col = p.col(rowId(p, r)).intValue
        if (showsAppsRow(p) && r == 0) {
            apps.getOrNull(col)?.let { launch(it.key, it.launchIntent) }
            return
        }
        val title = titleAt(p, r, col) ?: return
        launch(cardKey(rowId(p, r), title.id), productPage(title))
    }

    private fun focusedApp(p: TabPage): AppEntry? =
        if (showsAppsRow(p) && p.row == 0) apps.getOrNull(p.col(APPS_ROW).intValue) else null

    /** Play opens the streaming app it's featured on, else one that carries it, else its trailer. */
    private fun play(item: HeroItem) {
        installedApp(item.providerPackage)?.let { return launch(KEY_PLAY, it.launchIntent) }
        pressedKey = KEY_PLAY
        viewModelScope.launch {
            val t = item.title
            val ids = tmdb.watchProviders(t.tmdbId, t.mediaType, region)
                .ifEmpty { if (region != FALLBACK_REGION) tmdb.watchProviders(t.tmdbId, t.mediaType, FALLBACK_REGION) else emptyList() }
            val carrier = ids.firstNotNullOfOrNull { installedApp(Providers.packageFor(it)) }
            launch(KEY_PLAY, carrier?.launchIntent ?: trailer(t))
        }
    }

    private fun installedApp(pkg: String?): AppEntry? = pkg?.let { p -> allApps.firstOrNull { it.packageName == p } }

    /**
     * The title's trailer search on YouTube: its search intent if the installed YouTube takes one,
     * else the results URL in YouTube, else the browser, else just YouTube itself.
     */
    private fun trailer(t: Title): Intent {
        val query = "${t.name} trailer"
        val url = Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(query))
        val youTube = installedApp(Providers.YOUTUBE_PACKAGE)
        val candidates = listOfNotNull(
            youTube?.let { Intent(Intent.ACTION_SEARCH).setPackage(it.packageName).putExtra(SearchManager.QUERY, query) },
            youTube?.let { Intent(Intent.ACTION_VIEW, url).setPackage(it.packageName) },
            Intent(Intent.ACTION_VIEW, url),
        )
        val pm = getApplication<Application>().packageManager
        val pick = candidates.firstOrNull { it.resolveActivity(pm) != null } ?: youTube?.launchIntent ?: candidates.last()
        return Intent(pick).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun productPage(t: Title): Intent =
        Intent(getApplication(), SearchActivity::class.java)
            .putExtra(EXTRA_TMDB_ID, t.tmdbId)
            .putExtra(EXTRA_TMDB_TYPE, t.mediaType)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    // ── Editing & the action sheet ───────────────────────────────────────────────────────────

    private fun finishEditing() {
        persistOrder(apps)
        editing = false
    }

    /** Saves the visible order; hidden apps keep their relative order after it. */
    private fun persistOrder(visible: List<AppEntry>) {
        val visibleKeys = visible.mapTo(HashSet()) { it.key }
        allApps = visible + allApps.filter { it.key !in visibleKeys }
        order.save(allApps)
    }

    private fun openSheet(app: AppEntry?, fromEditing: Boolean) {
        app ?: return
        val actions = buildList {
            if (!fromEditing) add(SheetAction.Move)
            if (!app.isPseudo) add(SheetAction.Hide)
            if (!app.isPseudo) add(SheetAction.Info)
            if (app.isRemovable) add(SheetAction.Uninstall)
            add(SheetAction.Cancel)
        }
        sheet = Sheet(app, actions)
    }

    private fun runSheetAction(action: SheetAction, app: AppEntry) {
        sheet = null
        when (action) {
            SheetAction.Move -> startMoving(app)
            SheetAction.Hide -> {
                if (editing) finishEditing()
                prefs.update { it.copy(hiddenApps = it.hiddenApps + app.key) }
            }
            SheetAction.Info -> _intents.tryEmit(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            SheetAction.Uninstall -> {
                if (editing) finishEditing()
                _intents.tryEmit(
                    Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            SheetAction.Cancel -> Unit
        }
    }

    /** Moving happens in the Apps grid; from the Home row it jumps there with the app picked up. */
    private fun startMoving(app: AppEntry) {
        val index = apps.indexOfFirst { it.key == app.key }
        if (index < 0) return
        switchTab(Tab.Apps)
        barSlot = Bar.slotOf(Tab.Apps)
        val grid = pages.getValue(Tab.Apps)
        grid.gridFocus = index
        grid.row = 0
        editing = true
    }

    /** tvOS's highlighted state: the target dips and brightens for a beat, then the app opens. */
    private fun launch(pressKey: String, intent: Intent) {
        pressedKey = pressKey
        viewModelScope.launch {
            delay(PRESS_MS)
            _intents.emit(intent)
            delay(PRESS_MS)
            if (pressedKey == pressKey) pressedKey = null
        }
    }

    companion object {
        const val APPS_ROW = "apps"
        const val KEY_PLAY = "hero:play"
        const val KEY_MORE_INFO = "hero:info"
        const val EXTRA_TMDB_ID = "tmdb_id"
        const val EXTRA_TMDB_TYPE = "tmdb_type"
        private const val FALLBACK_REGION = "US"
        private const val PRESS_MS = 120L
        private const val PREFETCH_DELAY_MS = 4_000L

        fun barKey(slot: Int) = "bar:$slot"
        fun cardKey(rowId: String, titleId: String) = "card:$rowId/$titleId"
    }
}
