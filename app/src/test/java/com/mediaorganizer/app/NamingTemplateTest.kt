package com.mediaorganizer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class NamingTemplateTest {

    @Test
    fun extractsTagsFromReleaseFilenames() {
        val movieTags = MediaTagExtractor.extract(
            "Dune.Part.Two.2024.2160p.WEB-DL.DDP5.1.Atmos.DoVi.HDR10.H.265-GROUP"
        )
        assertEquals("2160p", movieTags.resolution)
        assertEquals("H.265", movieTags.codec)
        assertEquals("DDP5.1 Atmos", movieTags.audio)
        assertEquals("WEB-DL", movieTags.source)
        assertEquals("HDR10", movieTags.hdr)

        val tvTags = MediaTagExtractor.extract(
            "Severance.S01E02.1080p.BluRay.x265.AAC.5.1.EXTENDED"
        )
        assertEquals("1080p", tvTags.resolution)
        assertEquals("x265", tvTags.codec)
        assertEquals("AAC 5.1", tvTags.audio)
        assertEquals("BluRay", tvTags.source)
        assertEquals("EXTENDED", tvTags.edition)
    }

    @Test
    fun formatsMovieTemplateAndCleansMissingPlaceholders() {
        val fullInfo = MediaScanner.classifyMovie("Inception.2010.1080p.BluRay.x264.DTS.mkv")
        assertNotNull(fullInfo)
        val template = "{title} ({year}) [{resolution}] [{codec}] [{audio}] [{source}]"
        val formattedFull = NamingTemplateFormatter.formatMovieFilename(
            template = template,
            movieName = fullInfo!!.movieName,
            year = fullInfo.year,
            tags = fullInfo.tags,
            ext = "mkv"
        )
        assertEquals("Inception (2010) [1080p] [x264] [DTS] [BluRay].mkv", formattedFull)

        val sparseInfo = MediaScanner.classifyMovie("The Matrix (1999) 720p.mp4")
        assertNotNull(sparseInfo)
        val formattedSparse = NamingTemplateFormatter.formatMovieFilename(
            template = template,
            movieName = sparseInfo!!.movieName,
            year = sparseInfo.year,
            tags = sparseInfo.tags,
            ext = "mp4"
        )
        assertEquals("The Matrix (1999) [720p].mp4", formattedSparse)
    }

    @Test
    fun formatsTvTemplateWithAndWithoutEpisodeTitle() {
        val tvInfo = MediaScanner.classifyTvEpisode("Shows", "Breaking.Bad.S01E01.1080p.HEVC.AAC.WEB-DL.mkv")
        assertNotNull(tvInfo)

        val template = "{show} - {tag} - {episode_title} [{resolution}] [{codec}]"
        val withTitle = NamingTemplateFormatter.formatTvEpisodeFilename(
            template = template,
            showName = tvInfo!!.showName,
            season = tvInfo.season,
            episode = tvInfo.episode,
            episodeEnd = tvInfo.episodeEnd,
            tag = tvInfo.tag,
            episodeTitle = "Pilot",
            tags = tvInfo.tags,
            ext = "mkv"
        )
        assertEquals("Breaking Bad - S01E01 - Pilot [1080p] [HEVC].mkv", withTitle)

        val withoutTitle = NamingTemplateFormatter.formatTvEpisodeFilename(
            template = NamingTemplateFormatter.DEFAULT_TV_TEMPLATE,
            showName = tvInfo.showName,
            season = tvInfo.season,
            episode = tvInfo.episode,
            episodeEnd = tvInfo.episodeEnd,
            tag = tvInfo.tag,
            episodeTitle = null,
            tags = tvInfo.tags,
            ext = "mkv"
        )
        assertEquals("Breaking Bad S01E01.mkv", withoutTitle)
    }

    @Test
    fun extractsAudioLanguagesOnlyAndFormatsAudioLanguageTemplates() {
        val socialReckoning = MediaScanner.classifyMovie(
            "The.Social.Reckoning.2026.1080p.10Bit.WEB-DL.English.Hindi.ESubs.MoviesMod.fi.mkv"
        )
        assertNotNull(socialReckoning)
        assertEquals("English Hindi", socialReckoning!!.tags.audioLanguages)
        assertEquals(
            "The Social Reckoning 1080p WEB-DL English Hindi 2026.mkv",
            NamingTemplateFormatter.formatMovieFilename(
                template = "{title} {resolution} {source} {language} {year}",
                movieName = socialReckoning.movieName,
                year = socialReckoning.year,
                tags = socialReckoning.tags,
                ext = "mkv"
            )
        )
        assertEquals(
            "The Social Reckoning 1080p WEB-DL English Hindi 2026.mkv",
            NamingTemplateFormatter.formatMovieFilename(
                template = "{title} {resolution} {source} {audio_languages} {year}",
                movieName = socialReckoning.movieName,
                year = socialReckoning.year,
                tags = socialReckoning.tags,
                ext = "mkv"
            )
        )

        val simpsons = MediaScanner.classifyTvEpisode(
            "The Simpsons",
            "The.Simpsons.S03E01.Mr.Lisa.Goes.to.Washington.720p.10Bit.English.Msubs.MoviesMod.org.mkv"
        )
        assertNotNull(simpsons)
        assertEquals("English", simpsons!!.tags.audioLanguages)
        assertEquals("Mr Lisa Goes to Washington", simpsons.titleHint)

        // Language words inside movie/episode titles or next to subtitle markers are ignored
        val englishPatient = MediaScanner.classifyMovie("The.English.Patient.1996.1080p.BluRay.mkv")
        assertNotNull(englishPatient)
        assertEquals("", englishPatient!!.tags.audioLanguages)

        val frenchConnEp = MediaScanner.classifyTvEpisode("Show", "Show.S01E01.The.French.Connection.720p.x264.mkv")
        assertNotNull(frenchConnEp)
        assertEquals("", frenchConnEp!!.tags.audioLanguages)

        val subQualified = MediaScanner.classifyMovie("Your.Name.2016.1080p.Japanese.English.Sub.mkv")
        assertNotNull(subQualified)
        assertEquals("Japanese", subQualified!!.tags.audioLanguages)

        val subBefore = MediaScanner.classifyMovie("Parasite.2019.1080p.Korean.Sub.English.mkv")
        assertNotNull(subBefore)
        assertEquals("Korean", subBefore!!.tags.audioLanguages)
    }
}
