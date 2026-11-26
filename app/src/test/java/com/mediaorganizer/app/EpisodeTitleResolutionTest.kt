package com.mediaorganizer.app

import com.mediaorganizer.app.MediaScanner.MediaItem
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class EpisodeTitleResolutionTest {

    @Test
    fun resolvesShiftedSeasonEpisodesUsingFilenameTitleHints() {
        val seasonMap = mapOf(
            1 to "Stark Raving Dad",
            2 to "Mr. Lisa Goes to Washington",
            3 to "When Flanders Failed",
            4 to "Bart the Murderer",
            5 to "Homer Defined",
            6 to "Like Father Like Clown",
            7 to "Treehouse of Horror II",
            8 to "Lisa's Pony",
            9 to "Saturdays of Thunder",
            10 to "Flaming Moe's",
            11 to "Burns Verkaufen der Kraftwerk"
        )

        val files = listOf(
            "The.Simpsons.S03E01.Mr.Lisa.Goes.to.Washington.720p.10Bit.English.Msubs.MoviesMod.org.mkv",
            "The.Simpsons.S03E02.When.Flanders.Failed.720p.10Bit.English.Msubs.MoviesMod.org.mkv",
            "The.Simpsons.S03E03.Bart.the.Murderer.720p.10Bit.English.Msubs.MoviesMod.org.mkv",
            "The.Simpsons.S03E04.Homer.Defined.720p.10Bit.English.Msubs.MoviesMod.org.mkv",
            "The.Simpsons.S03E05.Like.Father.Like.Clown.720p.10Bit.English.Msubs.MoviesMod.org.mkv",
            "The.Simpsons.S03E06.Treehouse.of.Horror.II.720p.10Bit.English.Msubs.MoviesMod.org.mkv",
            "The.Simpsons.S03E07.Lisas.Pony.720p.10Bit.English.Msubs.MoviesMod.org.mkv",
            "The.Simpsons.S03E08.Saturdays.of.Thunder.720p.10Bit.English.Msubs.MoviesMod.org.mkv",
            "The.Simpsons.S03E09.Flaming.Moes.720p.10Bit.English.Msubs.MoviesMod.org.mkv",
            "The.Simpsons.S03E10.Burns.Verkaufen.der.Kraftwerk.720p.10Bit.English.Msubs.MoviesMod.org.mkv"
        )

        val items = files.map { filename ->
            val info = MediaScanner.classifyTvEpisode("The Simpsons", filename)!!
            MediaItem.TvEpisode(
                file = File(filename),
                originalName = filename,
                showName = info.showName,
                season = info.season,
                episode = info.episode,
                episodeEnd = info.episodeEnd,
                tag = info.tag,
                tags = info.tags,
                titleHint = info.titleHint
            )
        }

        val resolved = MediaTitleFetcher.resolveEpisodeTitlesForSeason(items, seasonMap)

        assertEquals("Mr. Lisa Goes to Washington", resolved[items[0]])
        assertEquals("When Flanders Failed", resolved[items[1]])
        assertEquals("Bart the Murderer", resolved[items[2]])
        assertEquals("Homer Defined", resolved[items[3]])
        assertEquals("Like Father Like Clown", resolved[items[4]])
        assertEquals("Treehouse of Horror II", resolved[items[5]])
        assertEquals("Lisa's Pony", resolved[items[6]])
        assertEquals("Saturdays of Thunder", resolved[items[7]])
        assertEquals("Flaming Moe's", resolved[items[8]])
        assertEquals("Burns Verkaufen der Kraftwerk", resolved[items[9]])
    }

    @Test
    fun resolvesUnhintedEpisodesNormallyByEpisodeNumber() {
        val seasonMap = mapOf(
            1 to "Pilot",
            2 to "Cat's in the Bag..."
        )
        val ep1 = MediaItem.TvEpisode(
            file = File("Breaking.Bad.S01E01.1080p.mkv"),
            originalName = "Breaking.Bad.S01E01.1080p.mkv",
            showName = "Breaking Bad",
            season = 1,
            episode = 1,
            episodeEnd = null,
            tag = "S01E01",
            titleHint = ""
        )
        val resolved = MediaTitleFetcher.resolveEpisodeTitlesForSeason(listOf(ep1), seasonMap)
        assertEquals("Pilot", resolved[ep1])
    }

    @Test
    fun parsesModernWikipediaHeadingWrapperAndSeasonColumn() {
        val html = """
            <html><body>
            <div class="mw-heading mw-heading2"><h2 id="Season_3">Season 3 (1991–1992)</h2></div>
            <table class="wikitable plainrowheaders wikiepisodetable">
              <tr>
                <th>No. overall</th>
                <th>No. in season</th>
                <th>Title</th>
              </tr>
              <tr class="vevent module-episode-list-row">
                <th>36</th>
                <td>1</td>
                <td class="summary">"Stark Raving Dad"<sup class="reference">[1]</sup></td>
              </tr>
              <tr class="vevent module-episode-list-row">
                <th>37</th>
                <td>2</td>
                <td class="summary">"Mr. Lisa Goes to Washington"</td>
              </tr>
            </table>
            </body></html>
        """.trimIndent()

        val parsed = WikipediaApi.parseWikiEpisodeTables(Jsoup.parse(html))
        assertEquals("Stark Raving Dad", parsed[3]?.get(1))
        assertEquals("Mr. Lisa Goes to Washington", parsed[3]?.get(2))
    }
}
