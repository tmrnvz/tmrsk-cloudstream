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
    override var name = "Famelack"
    override var lang = "tr"
    override val hasMainPage = true
    override val hasQuickSearch = false
    override val hasDownloadSupport = false
    override val supportedTypes = setOf(TvType.Live, TvType.TvSeries)

    private val mediaTypes = listOf(
        MediaType("tv", "Dünya TV"),
        MediaType("radio", "Dünya Radyoları"),
        MediaType("webcams", "Canlı Kameralar")
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val rows = mediaTypes.map { media ->
            val metadata = app.get("$mainUrl/${media.id}/raw/countries_metadata.json")
                .parsedSafe<Map<String, Any?>>().orEmpty()
                .mapNotNull { (code, rawInfo) ->
                    runCatching { parseJson<CountryMeta>(rawInfo.toJson()) }
                        .getOrNull()
                        ?.let { code to it }
                }
            val countries = metadata
                .filter { (_, info) -> info.hasChannels && info.channelCount > 0 }
                .sortedWith(compareBy<Pair<String, CountryMeta>>(
                    { if (it.first.equals("TR", true)) 0 else 1 },
                    { it.second.country }
                ))
                .map { (code, info) -> toCountryResponse(media, code.lowercase(), info) }
            HomePageList(media.title, countries, isHorizontalImages = false)
        }
        return newHomePageResponse(rows, hasNext = false)
    }

    private fun toCountryResponse(media: MediaType, code: String, info: CountryMeta): SearchResponse =
        newTvSeriesSearchResponse(
            "${flagEmoji(code)} ${info.country} (${info.channelCount})",
            CountrySelection(media.id, media.title, code, info.country).toJson(),
            TvType.TvSeries
        ) {
            posterUrl = "https://flagcdn.com/w320/${flagCode(code)}.png"
        }

    override suspend fun load(url: String): LoadResponse {
        val selection = parseJson<CountrySelection>(url)
        val stations = app.get("$mainUrl/${selection.mediaId}/raw/countries/${selection.code}.json")
            .parsedSafe<Array<Station>>()?.toList().orEmpty()
            .filter { it.sources.streams.isNotEmpty() || it.sources.youtube.isNotEmpty() }

        val episodes = stations.map { station ->
            newEpisode(PlayableStation(selection.mediaTitle, selection.country, station).toJson()) {
                name = station.name
                description = buildString {
                    if (station.languages.isNotEmpty()) append(station.languages.joinToString(", "))
                    if (station.isGeoBlocked) {
                        if (isNotEmpty()) append(" • ")
                        append("Coğrafi kısıtlamalı")
                    }
                }.ifBlank { null }
            }
        }

        return newTvSeriesLoadResponse(
            "${flagEmoji(selection.code)} ${selection.country} • ${selection.mediaTitle}",
            url,
            TvType.TvSeries,
            episodes
        ) {
            posterUrl = "https://flagcdn.com/w320/${flagCode(selection.code)}.png"
            plot = "Famelack açık veri kaynağındaki ${selection.country} yayınları"
            tags = listOf(selection.mediaTitle, selection.country, "Famelack")
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val playable = parseJson<PlayableStation>(data)
        var found = false

        playable.station.sources.streams.forEachIndexed { index, stream ->
            if (stream.isBlank()) return@forEachIndexed
            found = true
            val type = when {
                stream.contains(".m3u8", true) -> ExtractorLinkType.M3U8
                stream.contains(".mpd", true) -> ExtractorLinkType.DASH
                else -> ExtractorLinkType.VIDEO
            }
            callback(newExtractorLink(
                source = name,
                name = if (index == 0) playable.station.name else "${playable.station.name} ${index + 1}",
                url = stream,
                type = type
            ) {
                referer = "https://famelack.com/"
                quality = Qualities.Unknown.value
                headers = mapOf("Referer" to "https://famelack.com/")
            })
        }

        playable.station.sources.youtube.forEach { embedUrl ->
            val videoId = embedUrl.substringAfter("/embed/", "").substringBefore('?').substringBefore('/')
            if (videoId.isNotBlank()) {
                found = loadExtractor(
                    "https://www.youtube.com/watch?v=$videoId",
                    "https://famelack.com/",
                    subtitleCallback,
                    callback
                ) || found
            }
        }
        return found
    }

    data class MediaType(val id: String, val title: String)
    data class CountryMeta(
        val country: String = "",
        val capital: String = "",
        val timeZone: String = "",
        val hasChannels: Boolean = false,
        val channelCount: Int = 0
    )
    data class CountrySelection(
        val mediaId: String,
        val mediaTitle: String,
        val code: String,
        val country: String
    )
    data class Sources(
        val streams: List<String> = emptyList(),
        val youtube: List<String> = emptyList()
    )
    data class Station(
        val nanoid: String = "",
        val name: String = "",
        val sources: Sources = Sources(),
        val languages: List<String> = emptyList(),
        val country: String = "",
        val isGeoBlocked: Boolean = false
    )
    data class PlayableStation(
        val mediaTitle: String,
        val country: String,
        val station: Station
    )

    private fun flagCode(code: String): String = if (code.equals("uk", true)) "gb" else code.lowercase()

    private fun flagEmoji(code: String): String = flagCode(code).uppercase()
        .map { char -> String(Character.toChars(0x1F1E6 + (char.code - 'A'.code))) }
        .joinToString("")
}
