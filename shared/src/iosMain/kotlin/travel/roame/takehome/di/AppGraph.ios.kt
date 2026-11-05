package travel.roame.takehome.di

import io.ktor.client.engine.darwin.Darwin
import travel.roame.takehome.data.local.appDatabaseBuilder
import travel.roame.takehome.data.local.configure

fun createAppGraph(baseUrl: String): AppGraph =
    AppGraph(appDatabaseBuilder().configure(), Darwin.create(), baseUrl)
