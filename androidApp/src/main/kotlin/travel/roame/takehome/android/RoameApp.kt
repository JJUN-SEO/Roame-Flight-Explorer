package travel.roame.takehome.android

import android.app.Application
import android.os.StrictMode
import travel.roame.takehome.di.AppGraph
import travel.roame.takehome.di.createAppGraph

/** Creates the one shared store for the whole process (D10). */
class RoameApp : Application() {

    val graph: AppGraph by lazy { createAppGraph(this, BuildConfig.API_BASE_URL) }

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            // PERF-2: report any disk or network access on the main thread.
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder().detectDiskReads().detectDiskWrites().detectNetwork().penaltyLog().build(),
            )
        }
    }
}
