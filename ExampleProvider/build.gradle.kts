version = 1

cloudstream {
    // All of these properties are optional, you can safely remove any of them.

    description = "Türkiye açık canlı yayınları ve PuhuTV"
    authors = listOf("tmrsk")

    /**
    * Status int as one of the following:
    * 0: Down
    * 1: Ok
    * 2: Slow
    * 3: Beta-only
    **/
    status = 1

    tvTypes = listOf("Live", "Movie", "TvSeries")

    requiresResources = false
    language = "tr"

    // Random CC logo I found
    iconUrl = "https://puhutv-image.akamaized.net/19-07/13/puhutv_logo_300x300.jpg"
}
