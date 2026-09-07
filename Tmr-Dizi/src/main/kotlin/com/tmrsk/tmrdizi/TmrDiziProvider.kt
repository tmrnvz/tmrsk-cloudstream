package com.tmrsk.tmrdizi

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink

class TmrDiziProvider : MainAPI() {
    override var mainUrl = "https://raw.githubusercontent.com/tmrnvz/tmrsk-cloudstream/main/playlists/Tmr-Dizi.m3u"
    override var name = "Tmr-Dizi"
    override var lang = "tr"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override val hasDownloadSupport = false
    override val supportedTypes = setOf(TvType.TvSeries)

    private var cachedChannels: List<Channel>? = null

    private suspend fun channels(): List<Channel> {
        cachedChannels?.let { return it }
        return parseM3u(app.get(mainUrl).body.string()).also { cachedChannels = it }
    }

    private suspend fun series(): List<Series> = channels()
        .groupBy { canonicalSeriesName(it.group) }
        .map { (title, episodes) ->
            Series(
                title = title,
                poster = episodes.firstOrNull { it.logo.isNotBlank() }?.logo.orEmpty()
            )
        }
        .sortedBy { it.title.lowercase() }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val rows = series()
            .groupBy { alphabetGroup(it.title) }
            .toSortedMap(compareBy<String> { if (it == "#") 1 else 0 }.thenBy { it })
            .map { (letter, items) ->
                HomePageList(
                    "$letter Dizileri",
                    items.map(::toSearchResponse),
                    isHorizontalImages = true
                )
            }
        return newHomePageResponse(rows, hasNext = false)
    }

    override suspend fun search(query: String): List<SearchResponse> = series()
        .filter { it.title.contains(query, ignoreCase = true) }
        .map(::toSearchResponse)

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    private fun toSearchResponse(series: Series): TvSeriesSearchResponse =
        newTvSeriesSearchResponse(series.title, SeriesRef(series.title).toJson(), TvType.TvSeries) {
            posterUrl = series.poster.ifBlank { null }
        }

    override suspend fun load(url: String): LoadResponse {
        val ref = parseJson<SeriesRef>(url)
        val seriesEpisodes = channels()
            .filter { canonicalSeriesName(it.group).equals(ref.title, ignoreCase = true) }
            .mapIndexed { index, channel -> toEpisode(channel, index) }
            .sortedWith(compareBy<Episode> { it.season ?: 1 }.thenBy { it.episode ?: 0 }.thenBy { it.name.orEmpty() })

        val poster = seriesEpisodes.firstOrNull { !it.posterUrl.isNullOrBlank() }?.posterUrl
        return newTvSeriesLoadResponse(ref.title, url, TvType.TvSeries, seriesEpisodes) {
            posterUrl = poster
            plot = "Tmr-Dizi • ${seriesEpisodes.size} bölüm"
        }
    }

    private fun toEpisode(channel: Channel, fallbackIndex: Int): Episode {
        val season = SEASON_REGEX.find(channel.title)?.groupValues?.get(1)?.toIntOrNull() ?: 1
        val episode = EPISODE_REGEX.find(channel.title)?.groupValues?.get(1)?.toIntOrNull()
            ?: fallbackIndex + 1
        val audio = AUDIO_REGEX.find(channel.title)?.value?.lowercase()?.replaceFirstChar { it.uppercase() }

        return newEpisode(channel.toJson()) {
            name = audio ?: "Bölüm $episode"
            this.season = season
            this.episode = episode
            posterUrl = channel.logo.ifBlank { null }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val channel = parseJson<Channel>(data)
        callback(newExtractorLink(
            source = name,
            name = channel.title,
            url = channel.url,
            type = when {
                channel.url.contains(".m3u8", true) -> ExtractorLinkType.M3U8
                channel.url.contains(".mpd", true) -> ExtractorLinkType.DASH
                else -> ExtractorLinkType.VIDEO
            }
        ) {
            referer = channel.referer
            quality = Qualities.Unknown.value
            headers = buildMap {
                if (channel.userAgent.isNotBlank()) put("User-Agent", channel.userAgent)
                if (channel.referer.isNotBlank()) put("Referer", channel.referer)
            }
        })
        return true
    }

    data class SeriesRef(val title: String)
    data class Series(val title: String, val poster: String = "")

    data class Channel(
        val title: String,
        val url: String,
        val logo: String = "",
        val group: String = "Diğer",
        val referer: String = "",
        val userAgent: String = ""
    )

    private fun parseM3u(content: String): List<Channel> {
        val result = mutableListOf<Channel>()
        var info: String? = null
        var referer = ""
        var userAgent = ""

        content.lineSequence().map(String::trim).filter(String::isNotEmpty).forEach { line ->
            when {
                line.startsWith("#EXTINF:", true) -> {
                    info = line
                    referer = ""
                    userAgent = ""
                }
                line.startsWith("#EXTVLCOPT:http-referrer=", true) ->
                    referer = line.substringAfter("=")
                line.startsWith("#EXTVLCOPT:http-user-agent=", true) ->
                    userAgent = line.substringAfter("=")
                !line.startsWith("#") && info != null -> {
                    val metadata = info!!
                    result += Channel(
                        title = metadata.substringAfter(",", "Bölüm").trim(),
                        url = line.substringBefore("|"),
                        logo = attribute(metadata, "tvg-logo"),
                        group = attribute(metadata, "group-title").ifBlank { "Diğer" },
                        referer = referer.ifBlank { headerFromUrl(line, "referer") },
                        userAgent = userAgent.ifBlank { headerFromUrl(line, "user-agent") }
                    )
                    info = null
                }
            }
        }
        return result.distinctBy { "${it.group.lowercase()}|${it.title.lowercase()}|${it.url}" }
    }

    private fun canonicalSeriesName(group: String): String {
        val title = group.trim()
        return when {
            title.equals("Friends Dizisi", ignoreCase = true) -> "Friends"
            title.equals("Forever", ignoreCase = true) -> "Forever"
            title.matches(Regex("(?i)^The Simpsons\\s+27\\s*-\\s*32\\.\\s*Sezonlar$")) -> "The Simpsons"
            title.matches(Regex("(?i)^Family Guy\\s*\\(13\\s*-\\s*24\\)$")) -> "Family Guy"
            else -> title
        }
    }

    private fun alphabetGroup(title: String): String {
        val first = title.firstOrNull()?.uppercaseChar() ?: return "#"
        return if (first.isLetter()) first.toString() else "#"
    }

    private fun attribute(line: String, key: String): String =
        Regex("""$key="([^"]*)"""").find(line)?.groupValues?.get(1).orEmpty()

    private fun headerFromUrl(line: String, key: String): String =
        line.substringAfter("|", "").split("&")
            .firstOrNull { it.startsWith("$key=", ignoreCase = true) }
            ?.substringAfter("=").orEmpty()

    companion object {
        private val SEASON_REGEX = Regex("(?i)(?:^|\\s|-)\\s*(\\d+)\\.\\s*S(?:ezon)?\\b")
        private val EPISODE_REGEX = Regex("(?i)Bölüm\\s*(\\d+)")
        private val AUDIO_REGEX = Regex("(?i)Dublaj|Altyazı")
    }
}
