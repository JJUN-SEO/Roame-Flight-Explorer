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
        val store = (application as RoameApp).graph.store
        setContent {
            RoameTheme { ExplorerApp(store) }
        }
    }
}
