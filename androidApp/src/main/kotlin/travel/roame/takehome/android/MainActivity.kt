package travel.roame.takehome.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import travel.roame.takehome.android.ui.ExplorerApp
import travel.roame.takehome.android.ui.RoameTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val graph = (application as RoameApp).graph
        // TEST_PLAN §6: the "Fail next DB write" hook is reachable in debug builds only.
        val failNextWrite = if (BuildConfig.DEBUG) graph::failNextSavedWrite else null
        setContent {
            RoameTheme { ExplorerApp(graph.store, failNextWrite) }
        }
    }
}
