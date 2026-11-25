package app.tvo.tv

import app.tvo.tv.data.Tmdb
import android.app.Application
import app.tvo.tv.data.AddonClient
import app.tvo.tv.data.Store
import coil.ImageLoader
import coil.ImageLoaderFactory
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object Tvo {
    /** Monte d'un cran quand on revient dans l'appli après longtemps : les rangées se rechargent (nouveautés). */
    val refreshEpoch = androidx.compose.runtime.mutableIntStateOf(0)
    lateinit var store: Store
        private set
    lateinit var client: AddonClient
        private set

    val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    lateinit var app: Application
    fun init(app: Application) {
        if (::store.isInitialized) return
        this.app = app
        store = Store(app)
        Tmdb.loadRemoteCached()
        client = AddonClient(http)
    }
}

class TvoApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        // Rapport de plantage : l'erreur est gardée et affichée au prochain lancement (voir MainActivity)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                val sw = java.io.StringWriter(); e.printStackTrace(java.io.PrintWriter(sw))
                java.io.File(filesDir, "crash.txt").writeText("Fil : ${t.name}\n$sw".take(6000))
            }
            previous?.uncaughtException(t, e)
        }
        Tvo.init(this)
        app.tvo.tv.data.Sync.start()
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this).crossfade(false).okHttpClient(Tvo.http).build()
}
