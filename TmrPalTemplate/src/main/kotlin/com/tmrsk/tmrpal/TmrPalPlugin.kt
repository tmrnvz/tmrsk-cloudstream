package com.tmrsk.tmrpal

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class TmrPalPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(TmrPalProvider())
    }
}
