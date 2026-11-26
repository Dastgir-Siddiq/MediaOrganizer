package com.mediaorganizer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class MediaScannerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun ignoresAndroidCameraAndPhoneRecordings() {
        val cameraFiles = listOf(
            "VID_20240924_143022.mp4",
            "VID_20240924_143022_00.mp4",
            "VID_20240924_143022_HDR.mp4",
            "VID20240924143022.mp4",
            "VID_2024_09_24_14_30_22.mp4",
            "VID_2024_1.mp4",
            "VID_2024.mp4",
            "VID_1985.mp4",
            "PXL_20240924_143022123.mp4",
            "PXL_20240924_143022123.TS.mp4",
            "PXL_20240924_143022123.NIGHT.mp4",
            "MVIMG_20240924_143022.mp4",
            "20240924_143022.mp4",
            "20240924_142015.mp4",
            "20240924_193012.mp4",
            "20240924_1930.mp4",
            "240924_2015.mp4",
            "2024-09-24 14-30-22.mp4",
            "2024-09-24_14.30.22.mp4",
            "2024-09-24_1930.mp4",
            "2024_09_24_14_30_22.mp4",
            "IMG_1984.MOV",
            "IMG_2024.MOV",
            "IMG_2024 (1).MOV",
            "MVI_2015.MOV",
            "DSC_2012.MOV",
            "DSCN2015.MOV",
            "DSCF2018.MOV",
            "MOV_2019.mp4",
            "SAM_2024.MP4",
            "GOPR2024.MP4",
            "GH012024.MP4",
            "GX012024.MP4",
            "C001920.MP4",
            "C002024.MP4",
            "MAH02024.MP4",
            "DJI_2024.MP4",
            "DJI_20240924143022_0001_D.MP4",
            "PRO_VID_20240924_143022_00_001.mp4",
            "LRV_20240924_143022_01_001.mp4",
            "WIN_20240924_14_30_22_Pro.mp4",
            "RPReplay_Final1727162024.mp4",
            "Movie on 24-09-24 at 14.30.mov"
        )

        for (filename in cameraFiles) {
            assertNull("Camera recording should not be classified as movie: $filename", MediaScanner.classifyMovie(filename))
        }
    }

    @Test
    fun ignoresScreenRecordingsMessagingAndEditorClips() {
        val nonMovieFiles = listOf(
            "Screen_Recording_20240924_143022.mp4",
            "Screen_Recording_20240924_143022_YouTube.mp4",
            "Screen_Recording_2024-09-24-14-30-22.mp4",
            "ScreenRecording_09-24-2024_14-30-22_1.mp4",
            "Screenrecorder-2024-09-24-14-30-22-456.mp4",
            "Screenrecording_2024-09-24-14-30-22-123.mp4",
            "SVID_20240924_143022_1.mp4",
            "screen-20240924-143022.mp4",
            "XRecorder_20240924_01.mp4",
            "WhatsApp Video 2024-09-24 at 14.30.22.mp4",
            "WhatsApp Video 2024-09-24 at 2.30.22 PM.mp4",
            "VID-20240924-WA0001.mp4",
            "IMG-20240924-WA0001.mp4",
            "video_2024-09-24_14-30-22.mp4",
            "InShot_20240924_143022123.mp4",
            "YouCut_20240924_143022123.mp4",
            "VN20240924_143022.mp4",
            "lv_0_20240924143022.mp4",
            "Snapchat-1727162024.mp4",
            "FB_VID_1727162024.mp4",
            "Game Name 2024.09.24 - 14.30.22.01.DVR.mp4"
        )

        for (filename in nonMovieFiles) {
            assertNull("Recording should not be classified as movie: $filename", MediaScanner.classifyMovie(filename))
        }
    }

    @Test
    fun ignoresResolutionsGenericNumericAndDateFiles() {
        val numericFiles = listOf(
            "video_1920x1080.mp4",
            "clip_1920_1080.mp4",
            "clip 1920 x 1080.mp4",
            "clip_1080_1920.mp4",
            "clip_2048_1080.mp4",
            "clip_1920p.mp4",
            "0001_2024.mp4",
            "143022_2024.mp4",
            "12_34_2024.mp4",
            "2024_1930.mp4",
            "REC_2024.mp4",
            "CAM_1_2024.mp4",
            "video_2024.mp4",
            "video_2024_1080p.mp4",
            "recording_2024.mp4",
            "Recording (1) 2024.mp4",
            "camera_2024.mp4",
            "SAM_2024.MP4",
            "Birthday_2024_09_24.mp4",
            "Vacation 24-09-2024.mp4",
            "Holiday 20240924.mp4",
            "Clip_14.30.22_2024.mp4",
            "Clip_2099.mp4",
            "Blade.Runner.2049.mp4"
        )

        for (filename in numericFiles) {
            assertNull("Numeric non-movie file should not be classified as movie: $filename", MediaScanner.classifyMovie(filename))
        }
    }

    @Test
    fun stillClassifiesRealMoviesAccurately() {
        val cases = listOf(
            "Inception.2010.1080p.BluRay.x264.YIFY.mp4" to ("Inception" to "2010"),
            "The Matrix (1999).mkv" to ("The Matrix" to "1999"),
            "Inception [2010].mkv" to ("Inception" to "2010"),
            "1917 (2019).mkv" to ("1917" to "2019"),
            "1917.2019.1080p.WEB-DL.mkv" to ("1917" to "2019"),
            "2001.A.Space.Odyssey.1968.1080p.BluRay.mkv" to ("2001 A Space Odyssey" to "1968"),
            "Wonder.Woman.1984.2020.2160p.WEB.mkv" to ("Wonder Woman 1984" to "2020"),
            "Blade Runner 2049 (2017).mkv" to ("Blade Runner 2049" to "2017"),
            "Blade.Runner.2049.2017.1080p.BluRay.mkv" to ("Blade Runner 2049" to "2017"),
            "2046 (2004).mkv" to ("2046" to "2004"),
            "1984 (1984).mkv" to ("1984" to "1984"),
            "2012 (2009).mkv" to ("2012" to "2009"),
            "1920 (2008).mkv" to ("1920" to "2008"),
            "300.2006.1080p.BluRay.mkv" to ("300" to "2006"),
            "12.Angry.Men.1957.720p.BluRay.mkv" to ("12 Angry Men" to "1957"),
            "9 (2009).mkv" to ("9" to "2009"),
            "Ocean's.11.2001.1080p.BluRay.mkv" to ("Ocean's 11" to "2001"),
            "REC (2007).mkv" to ("REC" to "2007"),
            "REC.2007.1080p.BluRay.mkv" to ("REC" to "2007"),
            "Snow White (1937).mkv" to ("Snow White" to "1937"),
            "Time Lapse (2014).mkv" to ("Time Lapse" to "2014"),
            "Camera Obscura (2017).mkv" to ("Camera Obscura" to "2017"),
            "Sam (2024).mkv" to ("Sam" to "2024"),
            "Kalki.2898.AD.2024.1080p.WEB-DL.mkv" to ("Kalki 2898 AD" to "2024"),
            "Movie.Title.2024.1920x1080.mp4" to ("Movie Title" to "2024"),
            "www.1TamilMV.Durban - Maharaja (2024) Tamil HQ HDRip - 1080p - x264 - AAC - 2.4GB.mkv" to ("Maharaja" to "2024"),
            "[YTS.MX] Deadpool & Wolverine (2024) [1080p] [WEBRip].mp4" to ("Deadpool & Wolverine" to "2024"),
            "Dune (Part 1) (2021).mkv" to ("Dune (Part 1)" to "2021")
        )

        for ((filename, expected) in cases) {
            val info = MediaScanner.classifyMovie(filename)
            assertNotNull("Real movie should be classified: $filename", info)
            assertEquals("Movie name mismatch for $filename", expected.first, info!!.movieName)
            assertEquals("Movie year mismatch for $filename", expected.second, info.year)
        }
    }

    @Test
    fun scanDirectorySkipsCameraSubfoldersAndRecordingsWhileKeepingMoviesAndTv() {
        val root = tempFolder.newFolder("Download")
        val cameraDir = File(root, "Camera").apply { mkdirs() }
        val screenDir = File(root, "Screenrecordings").apply { mkdirs() }
        val dcimDir = File(root, "DCIM").apply { mkdirs() }
        val moviesDir = File(root, "Movies").apply { mkdirs() }

        // Renamed and raw camera recordings inside Camera/DCIM/Screenrecordings folders
        File(cameraDir, "Birthday 2024.mp4").writeText("cam")
        File(screenDir, "Demo 2024.mp4").writeText("scr")
        File(dcimDir, "Vacation 2024.mp4").writeText("dcim")

        // Camera recordings directly inside source Download folder
        File(root, "VID_20240924_143022.mp4").writeText("vid")
        File(root, "20240924_193012.mp4").writeText("samsung")
        File(root, "IMG_2024.MOV").writeText("iphone")
        File(root, "WhatsApp Video 2024-09-24 at 14.30.22.mp4").writeText("wa")

        // Real movie and real TV episode (including dated TV show episode)
        File(moviesDir, "Dune.Part.Two.2024.1080p.WEB-DL.mkv").writeText("movie")
        File(root, "Severance.S01E01.1080p.mkv").writeText("tv")
        File(root, "The.Daily.Show.2024.09.24.S29E100.1080p.mkv").writeText("daily")

        val scanned = MediaScanner.scanDirectory(root)
        val names = scanned.map { it.originalName }.toSet()

        assertEquals(3, scanned.size)
        assertTrue(names.contains("Dune.Part.Two.2024.1080p.WEB-DL.mkv"))
        assertTrue(names.contains("Severance.S01E01.1080p.mkv"))
        assertTrue(names.contains("The.Daily.Show.2024.09.24.S29E100.1080p.mkv"))
    }

    @Test
    fun excludedPathsSupportExactPrefixBoundariesAndKeywords() {
        val root = tempFolder.newFolder("Storage")
        val dcim = File(root, "DCIM").apply { mkdirs() }
        val dcimMovies = File(root, "DCIM_Movies").apply { mkdirs() }
        val recordings = File(root, "Recordings").apply { mkdirs() }

        File(dcim, "Movie.2024.1080p.mkv").writeText("ignored")
        File(dcimMovies, "Avatar.2009.1080p.mkv").writeText("kept")
        File(recordings, "Trip.2024.1080p.mkv").writeText("ignored-by-keyword")

        val scanned = MediaScanner.scanDirectory(
            root,
            excludedPaths = listOf(dcim.absolutePath, "Recordings")
        )
        val names = scanned.map { it.originalName }.toSet()

        assertEquals(setOf("Avatar.2009.1080p.mkv"), names)
    }
}
