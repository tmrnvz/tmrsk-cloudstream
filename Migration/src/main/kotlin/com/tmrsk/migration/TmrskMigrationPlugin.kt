package com.tmrsk.migration

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class TmrskMigrationPlugin : Plugin() {
    override fun load(context: Context) = Unit
}
