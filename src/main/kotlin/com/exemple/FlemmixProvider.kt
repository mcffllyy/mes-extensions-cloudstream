package com.exemple

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.SearchResponse
import org.jsoup.nodes.Document

class FlemmixProvider : MainAPI() {
    override var mainUrl = "https://flemmix.kim"
    override var name = "Flemmix"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query"
        val response = app.get(url).text
        val document = org.jsoup.Jsoup.parse(response)

        return document.select(".result-item, .item, .movies-list .movie").mapNotNull { element ->
            val title = element.selectFirst(".title a, h3 a")?.text() ?: return@mapNotNull null
            val link = element.selectFirst(".title a, h3 a")?.attr("href") ?: return@mapNotNull null
            val poster = element.selectFirst("img")?.attr("src")
            val type = if (link.contains("/serie") || link.contains("/tv")) TvType.TvSeries else TvType.Movie

            newMovieSearchResponse(title, link, type) {
                this.posterUrl = poster
            }
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val response = app.get(url).text
        val document = org.jsoup.Jsoup.parse(response)

        val title = document.selectFirst("h1, .data h1")?.text() ?: return null
        val poster = document.selectFirst(".poster img")?.attr("src")
        val plot = document.selectFirst(".wp-content p, .description p")?.text()
        val type = if (url.contains("/serie") || url.contains("/tv")) TvType.TvSeries else TvType.Movie

        if (type == TvType.TvSeries) {
            val episodes = document.select(".episodios li").mapNotNull { epi ->
                val epiLink = epi.selectFirst("a")?.attr("href") ?: return@mapNotNull null
                val name = epi.selectFirst(".numerando")?.text() ?: "Épisode"
                Episode(epiLink, name)
            }
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = plot
            }
        } else {
            return newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = plot
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCdn: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val response = app.get(data).text
        val document = org.jsoup.Jsoup.parse(response)
        val serverElements = document.select(".dooplay_player_option, .player-option, li[data-post]")
        
        if (serverElements.isEmpty()) {
            document.select("iframe, .play-box iframe").forEach { iframe ->
                val src = iframe.attr("src")
                if (src.isNotEmpty() && !src.contains("about:blank")) {
                    val fixedUrl = if (src.startsWith("//")) "https:$src" else src
                    loadExtractor(fixedUrl, subtitleCallback, callback)
                }
            }
        } else {
            serverElements.forEach { element ->
                val postId = element.attr("data-post")
                val nume = element.attr("data-nume")
                val type = element.attr("data-type")

                if (postId.isNotEmpty() && nume.isNotEmpty()) {
                    val ajaxUrl = "$mainUrl/wp-admin/admin-ajax.php"
                    val ajaxResponse = app.post(
                        ajaxUrl,
                        headers = mapOf("X-Requested-With" to "XMLHttpRequest"),
                        data = mapOf("action" to "doo_player_ajax", "post" to postId, "nume" to nume, "type" to type)
                    ).text

                    val iframeUrl = Regex("""src=["']([^"']+)["']""").find(ajaxResponse)?.groupValues?.get(1)
                    if (!iframeUrl.isNullOrEmpty()) {
                        val fixedUrl = if (iframeUrl.startsWith("//")) "https:$iframeUrl" else iframeUrl
                        loadExtractor(fixedUrl, subtitleCallback, callback)
                    }
                }
            }
        }
        return true
    }
}