package com.tmrsk

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.AppUtils.parseJson

class PuhuTvProvider : MainAPI() {
    override var mainUrl = "https://puhutv.com"
    override var name = "PuhuTV"
    override var lang = "tr"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override val hasDownloadSupport = false
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    private suspend fun home(): List<PuhuContainer> {
        val json = app.get(mainUrl).document.selectFirst("script#__NEXT_DATA__")?.data()
            ?: throw ErrorLoadingException("PuhuTV ana sayfa verisi bulunamadı")
        return parseJson<NextData>(json).props.pageProps.data.data.containerItems
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val rows = home().mapNotNull { container ->
            val cards = container.items.mapNotNull(::toSearchResponse).distinctBy { it.url }
            if (cards.isEmpty()) null else HomePageList(container.title.ifBlank { "Öne Çıkanlar" }, cards)
        }
        return newHomePageResponse(rows, hasNext = false)
    }

    override suspend fun search(query: String): List<SearchResponse> = home().flatMap { it.items }
        .filter { it.name.contains(query, ignoreCase = true) }.mapNotNull(::toSearchResponse).distinctBy { it.url }
    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    private fun toSearchResponse(item: PuhuItem): SearchResponse? {
        val path = item.meta.webUrl.ifBlank { item.meta.videoWebUrl }
        if (path.isBlank()) return null
        val url = if (path.startsWith("http")) path else "$mainUrl/${path.trimStart('/')}"
        val poster = item.imageVerticalWebMobile.ifBlank { item.imageVerticalMobile.ifBlank { item.image } }
        return if (url.endsWith("-detay") && !item.type.contains("movie", true)) newTvSeriesSearchResponse(item.name, url, TvType.TvSeries) { posterUrl = poster }
        else newMovieSearchResponse(item.name, url, TvType.Movie) { posterUrl = poster }
    }

    override suspend fun load(url: String): LoadResponse {
        val slug = url.substringBefore("?").substringAfterLast("/")
        val detail = app.get("$mainUrl/api/slug/$slug").parsedSafe<ApiResponse<PuhuDetail>>()?.data
            ?: throw ErrorLoadingException("PuhuTV içeriği yüklenemedi")
        val poster = detail.assets.firstOrNull()?.content?.images?.wide?.bestImage()
        if (slug.endsWith("-izle") || detail.seasons.isEmpty()) {
            val title = detail.displayName.ifBlank { detail.name.ifBlank { detail.title.name } }
            val assetId = detail.assets.firstOrNull()?.id ?: detail.id
            return newMovieLoadResponse(title, url, TvType.Movie, assetId.toString()) {
                posterUrl = poster
                plot = detail.description.ifBlank { detail.title.description }
                year = detail.releasedAt ?: detail.title.releasedAt
            }
        }

        val episodes = mutableListOf<Episode>()
        detail.seasons.sortedBy { it.position }.forEach { season ->
            var page = 1
            var hasMore: Boolean
            do {
                val seasonData = app.get("https://appservice.puhutv.com/api/seasons/${season.id}/episodes?v=2&page=$page&per=100")
                    .parsedSafe<ApiResponse<SeasonPage>>()?.data ?: break
                episodes += seasonData.episodes.mapNotNull { asset ->
                    if (asset.id == 0L) null else newEpisode(asset.id.toString()) {
                        name = asset.displayName.ifBlank { asset.name }
                        this.season = asset.seasonNumber ?: season.position
                        episode = asset.episodeNumber ?: asset.meta?.position
                        posterUrl = asset.image.ifBlank { asset.content?.images?.wide?.bestImage().orEmpty() }.ifBlank { null }
                        description = asset.description ?: asset.meta?.shortDescription
                    }
                }
                hasMore = seasonData.hasMore
                page++
            } while (hasMore && page <= 10)
        }
        if (episodes.isEmpty()) episodes += detail.assets.map { asset ->
            newEpisode(asset.id.toString()) {
                name = asset.displayName.ifBlank { asset.name }
                season = asset.seasonNumber
                episode = asset.episodeNumber
                posterUrl = asset.content?.images?.wide?.bestImage()
            }
        }
        return newTvSeriesLoadResponse(detail.name, url, TvType.TvSeries, episodes) {
            posterUrl = poster
            plot = detail.description
            year = detail.releasedAt
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val videos = app.get("$mainUrl/api/assets/$data/videos").parsedSafe<ApiResponse<VideoData>>()?.data?.videos ?: return false
        videos.filter { it.url.isNotBlank() }.groupBy { it.url }.values.map { group -> group.maxByOrNull { it.quality ?: 0 }!! }.forEach { video ->
            if (video.url.isBlank()) return@forEach
            val isHls = video.streamType.equals("hls", true) || video.videoFormat.equals("hls", true) || video.url.contains(".m3u8", true)
            callback(newExtractorLink(
                source = name,
                name = video.quality?.let { "PuhuTV ${it}p" } ?: "PuhuTV",
                url = video.url,
                type = if (isHls) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
            ) {
                referer = "$mainUrl/"
                quality = video.quality ?: Qualities.Unknown.value
                headers = mapOf("Referer" to "$mainUrl/")
            })
        }
        return videos.isNotEmpty()
    }

    private fun Map<String, String>.bestImage(): String? {
        val raw = this["640x360"] ?: this["960x540"] ?: this["main"] ?: values.lastOrNull()
        return raw?.let { if (it.startsWith("http")) it else "https://${it.trimStart('/')}" }
    }

    data class NextData(val props: Props)
    data class Props(@JsonProperty("pageProps") val pageProps: PageProps)
    data class PageProps(val data: ApiResponse<ContainerData>)
    data class ApiResponse<T>(val data: T)
    data class ContainerData(@JsonProperty("container_items") val containerItems: List<PuhuContainer> = emptyList())
    data class PuhuContainer(val title: String = "", val items: List<PuhuItem> = emptyList())
    data class PuhuItem(val name: String = "", val image: String = "", val type: String = "", @JsonProperty("image_vertical_mobile") val imageVerticalMobile: String = "", @JsonProperty("image_vertical_web_mobile") val imageVerticalWebMobile: String = "", val meta: PuhuMeta = PuhuMeta())
    data class PuhuMeta(@JsonProperty("web_url") val webUrl: String = "", @JsonProperty("video_web_url") val videoWebUrl: String = "")
    data class PuhuDetail(val id: Long = 0, val name: String = "", @JsonProperty("display_name") val displayName: String = "", val description: String = "", @JsonProperty("released_at") val releasedAt: Int? = null, val seasons: List<PuhuSeason> = emptyList(), val assets: List<PuhuAsset> = emptyList(), val title: PuhuTitle = PuhuTitle())
    data class PuhuTitle(val name: String = "", val description: String = "", @JsonProperty("released_at") val releasedAt: Int? = null)
    data class PuhuSeason(val id: Long, val name: String = "", val position: Int = 1)
    data class SeasonPage(val episodes: List<PuhuAsset> = emptyList(), @JsonProperty("has_more") val hasMore: Boolean = false)
    data class PuhuAsset(val id: Long = 0, val name: String = "", @JsonProperty("display_name") val displayName: String = "", val description: String? = null, val image: String = "", @JsonProperty("season_number") val seasonNumber: Int? = null, @JsonProperty("episode_number") val episodeNumber: Int? = null, val content: PuhuContent? = null, val meta: AssetMeta? = null)
    data class AssetMeta(val position: Int? = null, @JsonProperty("short_description") val shortDescription: String? = null)
    data class PuhuContent(val images: PuhuImages? = null)
    data class PuhuImages(val wide: Map<String, String> = emptyMap())
    data class VideoData(val videos: List<PuhuVideo> = emptyList())
    data class PuhuVideo(val url: String = "", val quality: Int? = null, @JsonProperty("stream_type") val streamType: String = "", @JsonProperty("video_format") val videoFormat: String = "")
}
