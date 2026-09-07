package com.tmrsk.tmriptv

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink

class TmrIPTVProvider : MainAPI() {
    override var mainUrl = "https://raw.githubusercontent.com/tmrnvz/tmrsk-cloudstream/main/playlists/tmrIPTV.m3u"
    override var name = "tmrIPTV"
    override var lang = "tr"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override val hasDownloadSupport = false
    override val supportedTypes = setOf(TvType.Live)

    private suspend fun channels(): List<Channel> = parseM3u(app.get(mainUrl).text)

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val rows = channels().groupBy { it.group.ifBlank { "Diğer" } }.map { (group, items) ->
            HomePageList(group, items.map(::toSearchResponse), isHorizontalImages = true)
        }
        return newHomePageResponse(rows, hasNext = false)
    }

    override suspend fun search(query: String): List<SearchResponse> =
        channels().filter { it.title.contains(query, ignoreCase = true) }.map(::toSearchResponse)

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    private fun toSearchResponse(channel: Channel): LiveSearchResponse =
        newLiveSearchResponse(channel.title, channel.toJson(), TvType.Live) {
            posterUrl = channel.logo.ifBlank { null }
        }

    override suspend fun load(url: String): LoadResponse {
        val channel = parseJson<Channel>(url)
        return newLiveStreamLoadResponse(channel.title, channel.url, url) {
            posterUrl = channel.logo.ifBlank { null }
            plot = "tmrIPTV • ${channel.group}"
            tags = listOf(channel.group)
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
                        title = metadata.substringAfterLast(",").trim(),
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
        return result.distinctBy { it.title.lowercase() }
    }

    private fun attribute(line: String, key: String): String =
        Regex("""$key="([^"]*)"""").find(line)?.groupValues?.get(1).orEmpty()

    private fun headerFromUrl(line: String, key: String): String =
        line.substringAfter("|", "").split("&")
            .firstOrNull { it.startsWith("$key=", ignoreCase = true) }
            ?.substringAfter("=").orEmpty()
}
