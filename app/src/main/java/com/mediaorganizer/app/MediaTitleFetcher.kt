package com.mediaorganizer.app

object MediaTitleFetcher {

    fun getEpisodeTitles(
        showName: String,
        season: Int,
        episodeNumbers: List<Int>,
        wikiFirst: Boolean = false,
        onLog: (String) -> Unit = {}
    ): Map<Int, String> {
        val result = mutableMapOf<Int, String>()

        if (wikiFirst) {
            // 1. Try Wikipedia first
            try {
                onLog("Fetching titles from Wikipedia for $showName Season $season...")
                val wikiTitles = WikipediaApi.getTitlesFromWikipedia(showName, season, episodeNumbers)
                result.putAll(wikiTitles)
            } catch (e: Exception) {
                onLog("⚠️ Wikipedia error for $showName: ${e.message}")
            }

            val missing = episodeNumbers.filter { !result.containsKey(it) }
            if (missing.isEmpty()) return result

            // 2. Fall back to TVMaze for missing episodes
            try {
                onLog("Fetching missing titles from TVMaze for $showName Season $season...")
                val tvmazeTitles = TvMazeApi.getTitlesFromTvMaze(showName, season, missing)
                result.putAll(tvmazeTitles)
            } catch (e: Exception) {
                onLog("⚠️ TVMaze error for $showName: ${e.message}")
            }
        } else {
            // 1. Try TVMaze first (default)
            try {
                val tvmazeTitles = TvMazeApi.getTitlesFromTvMaze(showName, season, episodeNumbers)
                result.putAll(tvmazeTitles)
            } catch (e: Exception) {
                onLog("⚠️ TVMaze error for $showName: ${e.message}")
            }

            val missing = episodeNumbers.filter { !result.containsKey(it) }
            if (missing.isEmpty()) return result

            // 2. Try Wikipedia for missing episodes
            try {
                onLog("Fetching missing titles from Wikipedia for $showName Season $season...")
                val wikiTitles = WikipediaApi.getTitlesFromWikipedia(showName, season, missing)
                result.putAll(wikiTitles)
            } catch (e: Exception) {
                onLog("⚠️ Wikipedia error for $showName: ${e.message}")
            }
        }

        return result
    }
}
