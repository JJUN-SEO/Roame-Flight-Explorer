package travel.roame.takehome.android

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import travel.roame.takehome.android.ui.ExplorerApp
import travel.roame.takehome.android.ui.RoameTheme
import travel.roame.takehome.android.ui.isDark
import travel.roame.takehome.android.ui.rememberAppearance

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val graph = (application as RoameApp).graph
        // TEST_PLAN §6: the "Fail next DB write" hook is reachable in debug builds only.
        val failNextWrite = if (BuildConfig.DEBUG) graph::failNextSavedWrite else null
        setContent {
            // D39: the Account tab's appearance setting, saved between launches.
            val appearance = rememberAppearance()
            val dark = appearance.value.isDark()
            // The status bar's icons follow the app's own light or dark, not only the phone's.
            LaunchedEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                )
            }
            RoameTheme(dark) { ExplorerApp(graph.store, appearance, failNextWrite) }
        }
    }
}
