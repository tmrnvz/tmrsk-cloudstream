package com.tmrsk.tmrpal

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import java.net.URLEncoder

/**
 * Eğitim şablonu.
 *
 * tmrpal.example gerçek bir yayın sitesi değildir. Aşağıdaki yollar ve CSS
 * seçicileri, izinli bir sitenin yapısına göre değiştirilecek örneklerdir.
 */
class TmrPalProvider : MainAPI() {
    override var mainUrl = "https://tmrpal.example"
    override var name = "TmrPal"
    override var lang = "tr"

    override val hasMainPage = true
    override val hasQuickSearch = false
    override val hasDownloadSupport = false
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "/diziler/yabanci" to "Yabancı Diziler",
        "/diziler/yerli" to "Yerli Diziler",
        "/filmler" to "Filmler"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl${request.data}?page=$page").document

        // Bu seçiciler tamamen örnektir.
        val results = document.select(".content-card").mapNotNull { card ->
            val title = card.selectFirst(".title")?.text()?.trim().orEmpty()
            val href = card.selectFirst("a")?.attr("href").orEmpty()
            if (title.isBlank() || href.isBlank()) return@mapNotNull null

            val poster = card.selectFirst("img")
                ?.attr("data-src")
                ?.ifBlank { card.selectFirst("img")?.attr("src").orEmpty() }

            if (request.name == "Filmler") {
                newMovieSearchResponse(title, fixUrl(href), TvType.Movie) {
                    posterUrl = poster?.let(::fixUrl)
                }
            } else {
                newTvSeriesSearchResponse(title, fixUrl(href), TvType.TvSeries) {
                    posterUrl = poster?.let(::fixUrl)
                }
            }
        }

        return newHomePageResponse(
            HomePageList(request.name, results, isHorizontalImages = false),
            hasNext = results.isNotEmpty()
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val document = app.get("$mainUrl/arama?q=$encoded").document

        return document.select(".content-card").mapNotNull { card ->
            val title = card.selectFirst(".title")?.text()?.trim().orEmpty()
            val href = card.selectFirst("a")?.attr("href").orEmpty()
            if (title.isBlank() || href.isBlank()) return@mapNotNull null

            newTvSeriesSearchResponse(title, fixUrl(href), TvType.TvSeries) {
                posterUrl = card.selectFirst("img")?.attr("src")?.let(::fixUrl)
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val title = document.selectFirst("h1")?.text()?.trim() ?: "Başlıksız içerik"
        val poster = document.selectFirst(".poster img")?.attr("src")?.let(::fixUrl)
        val plot = document.selectFirst(".description")?.text()?.trim()

        val episodes = document.select(".episode-card").mapNotNull { episode ->
            val episodeName = episode.selectFirst(".episode-title")?.text()?.trim().orEmpty()
            val episodeUrl = episode.selectFirst("a")?.attr("href").orEmpty()
            if (episodeName.isBlank() || episodeUrl.isBlank()) return@mapNotNull null

            newEpisode(fixUrl(episodeUrl)) {
                name = episodeName
            }
        }

        return if (episodes.isNotEmpty()) {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                posterUrl = poster
                this.plot = plot
            }
        } else {
            // Film için yalnızca detay/meta veri gösterir; oynatma kapalıdır.
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                posterUrl = poster
                this.plot = plot
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // Bilerek uygulanmadı.
        // Yalnızca lisanslı/izinli video API veya doğrudan akış mevcutsa eklenmelidir.
        return false
    }
}
