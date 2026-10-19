package com.hamad.tvhome.search

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.hamad.tvhome.AppEntry
import com.hamad.tvhome.tmdb.TmdbItem

/** tvOS Search: linear keyboard, TMDB + installed-app results, product pages. */
class SearchActivity : ComponentActivity(), SearchActions {
    private lateinit var model: SearchModel

    private val voiceLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val text = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (res.resultCode == RESULT_OK && !text.isNullOrBlank()) model.replace(text)
    }

    override val voiceAvailable: Boolean by lazy {
        SpeechRecognizer.isRecognitionAvailable(this) && voiceIntent().resolveActivity(packageManager) != null
    }

    override val netflixInstalled: Boolean get() = netflixIntent() != null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        model = SearchModel(applicationContext)
        val deepLink = intent.getLongExtra(EXTRA_TMDB_ID, -1L).takeIf { it > 0 }?.let { id ->
            DeepLink(id, intent.getStringExtra(EXTRA_TMDB_TYPE) ?: "movie")
        }
        setContent { SearchApp(model, this, deepLink) }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if ((keyCode == KeyEvent.KEYCODE_SEARCH || keyCode == KeyEvent.KEYCODE_VOICE_ASSIST) && voiceAvailable) {
            if (event.repeatCount == 0) start(voiceIntent()) { voiceLauncher.launch(it) }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun launchApp(app: AppEntry) {
        start(app.launchIntent)
    }

    override fun watchTrailer(item: TmdbItem) {
        val query = "${item.title} trailer"
        val youTubeTv = Intent(Intent.ACTION_SEARCH)
            .setPackage(YOUTUBE_TV)
            .putExtra(SearchManager.QUERY, query)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (start(youTubeTv)) return
        val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(query)))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        start(web)
    }

    override fun openNetflix() {
        netflixIntent()?.let(::start)
    }

    override fun exit() = finish()

    private fun netflixIntent(): Intent? = NETFLIX_PACKAGES.firstNotNullOfOrNull { pkg ->
        packageManager.getLeanbackLaunchIntentForPackage(pkg) ?: packageManager.getLaunchIntentForPackage(pkg)
    }?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun voiceIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH)
        .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)

    private fun start(intent: Intent, launch: (Intent) -> Unit = ::startActivity): Boolean =
        try {
            launch(intent)
            true
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No activity for $intent", e)
            false
        } catch (e: SecurityException) {
            Log.w(TAG, "Not allowed to start $intent", e)
            false
        }

    companion object {
        const val EXTRA_TMDB_ID = "tmdb_id"
        const val EXTRA_TMDB_TYPE = "tmdb_type"
        private const val TAG = "TvSearch"
        private const val YOUTUBE_TV = "com.google.android.youtube.tv"
        private val NETFLIX_PACKAGES = listOf("com.netflix.ninja", "com.netflix.mediaclient")
    }
}
