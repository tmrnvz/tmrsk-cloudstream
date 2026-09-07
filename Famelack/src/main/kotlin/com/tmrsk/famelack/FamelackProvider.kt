package com.tmrsk.famelack

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink

class FamelackProvider : MainAPI() {
    override var mainUrl = "https://raw.githubusercontent.com/famelack/famelack-data/main"
    override var name = "Dünya TV"
    override var lang = "tr"
    override val hasMainPage = true
    override val hasQuickSearch = false
    override val hasDownloadSupport = false
    override val supportedTypes = setOf(TvType.Live, TvType.TvSeries)

    private val mediaId = "tv"
    private val mediaTitle = "Dünya TV"

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val turkey = stations("tr").map { stationResponse("tr", "Türkiye", it) }
        val countries = countryMetadata()
            .filter { (code, info) -> !code.equals("TR", true) && info.hasChannels && info.channelCount > 0 }
            .sortedBy { it.second.country }
            .map { (code, info) -> countryResponse(code.lowercase(), info) }
        return newHomePageResponse(listOf(
            HomePageList("🇹🇷 Türkiye Kanalları", turkey, isHorizontalImages = true),
            HomePageList("Diğer Ülkeler", countries, isHorizontalImages = false)
        ), hasNext = false)
    }

    private suspend fun countryMetadata(): List<Pair<String, CountryMeta>> =
        app.get("$mainUrl/$mediaId/raw/countries_metadata.json")
            .parsedSafe<Map<String, Any?>>().orEmpty()
            .mapNotNull { (code, raw) ->
                val json = raw?.toJson() ?: return@mapNotNull null
                runCatching { code to parseJson<CountryMeta>(json) }.getOrNull()
            }

    private suspend fun stations(code: String): List<Station> =
        app.get("$mainUrl/$mediaId/raw/countries/$code.json")
            .parsedSafe<Array<Station>>()?.toList().orEmpty()
            .filter { it.sources.streams.isNotEmpty() || it.sources.youtube.isNotEmpty() }

    private fun countryResponse(code: String, info: CountryMeta): SearchResponse =
        newTvSeriesSearchResponse(
            "${flagEmoji(code)} ${info.country} (${info.channelCount})",
            Route(code, info.country).toJson(),
            TvType.TvSeries
        ) { posterUrl = "https://flagcdn.com/w320/${flagCode(code)}.png" }

    private fun stationResponse(code: String, country: String, station: Station): SearchResponse =
        newLiveSearchResponse(station.name, Route(code, country, station).toJson(), TvType.Live) {
            posterUrl = station.logo.ifBlank { null }
        }

    override suspend fun load(url: String): LoadResponse {
        val route = parseJson<Route>(url)
        route.station?.let { station ->
            val uniqueUrl = station.sources.streams.firstOrNull() ?: station.sources.youtube.firstOrNull() ?: "$mainUrl/${route.code}/${station.name.hashCode()}"
            return newLiveStreamLoadResponse(station.name, uniqueUrl, url) {
                posterUrl = station.logo.ifBlank { null }
                plot = "${route.country} canlı televizyon yayını"
                tags = listOf(route.country, mediaTitle)
            }
        }
        val episodes = stations(route.code).map { station ->
            newEpisode(Route(route.code, route.country, station).toJson()) {
                name = station.name
                posterUrl = station.logo.ifBlank { null }
                description = if (station.isGeoBlocked) "Coğrafi kısıtlamalı" else null
            }
        }
        return newTvSeriesLoadResponse(
            "${flagEmoji(route.code)} ${route.country} • $mediaTitle",
            url, TvType.TvSeries, episodes
        ) {
            posterUrl = "https://flagcdn.com/w320/${flagCode(route.code)}.png"
            plot = "Famelack açık veri kaynağındaki ${route.country} televizyon kanalları"
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val station = parseJson<Route>(data).station ?: return false
        var found = false
        station.sources.streams.forEachIndexed { index, stream ->
            if (stream.isBlank()) return@forEachIndexed
            found = true
            callback(newExtractorLink(name, if (index == 0) station.name else "${station.name} ${index + 1}", stream,
                when { stream.contains(".m3u8", true) -> ExtractorLinkType.M3U8; stream.contains(".mpd", true) -> ExtractorLinkType.DASH; else -> ExtractorLinkType.VIDEO }
            ) {
                referer = "https://famelack.com/"
                quality = Qualities.Unknown.value
                headers = mapOf("Referer" to "https://famelack.com/")
            })
        }
        station.sources.youtube.forEach { embed ->
            val id = embed.substringAfter("/embed/", "").substringBefore('?').substringBefore('/')
            if (id.isNotBlank()) found = loadExtractor("https://www.youtube.com/watch?v=$id", "https://famelack.com/", subtitleCallback, callback) || found
        }
        return found
    }

    data class CountryMeta(val country: String = "", val hasChannels: Boolean = false, val channelCount: Int = 0)
    data class Sources(val streams: List<String> = emptyList(), val youtube: List<String> = emptyList())
    data class Station(val name: String = "", val logo: String = "", val sources: Sources = Sources(), val isGeoBlocked: Boolean = false)
    data class Route(val code: String, val country: String, val station: Station? = null)
    private fun flagCode(code: String) = if (code.equals("uk", true)) "gb" else code.lowercase()
    private fun flagEmoji(code: String) = flagCode(code).uppercase().map { String(Character.toChars(0x1F1E6 + (it.code - 'A'.code))) }.joinToString("")
}
