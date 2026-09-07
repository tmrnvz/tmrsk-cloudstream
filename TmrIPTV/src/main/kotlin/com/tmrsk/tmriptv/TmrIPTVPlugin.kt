package com.tmrsk.tmriptv

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class TmrIPTVPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(TmrIPTVProvider())
    }
}
