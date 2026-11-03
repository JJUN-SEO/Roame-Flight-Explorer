package travel.roame.takehome.di

import android.content.Context
import io.ktor.client.engine.okhttp.OkHttp
import travel.roame.takehome.data.local.appDatabaseBuilder
import travel.roame.takehome.data.local.configure

fun createAppGraph(context: Context, baseUrl: String): AppGraph =
    AppGraph(appDatabaseBuilder(context).configure(), OkHttp.create(), baseUrl)
