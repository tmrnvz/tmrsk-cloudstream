package com.tmrsk.tmrfilm

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class TmrFilmPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(TmrFilmProvider())
    }
}
