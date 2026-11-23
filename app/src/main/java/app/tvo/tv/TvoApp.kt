package app.tvo.tv

import android.app.Application
import app.tvo.tv.data.AddonClient
import app.tvo.tv.data.Store
import coil.ImageLoader
import coil.ImageLoaderFactory
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object Tvo {
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

    fun init(app: Application) {
        if (::store.isInitialized) return
        store = Store(app)
        client = AddonClient(http)
    }
}

class TvoApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        Tvo.init(this)
        app.tvo.tv.data.Sync.start()
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this).crossfade(250).okHttpClient(Tvo.http).build()
}
