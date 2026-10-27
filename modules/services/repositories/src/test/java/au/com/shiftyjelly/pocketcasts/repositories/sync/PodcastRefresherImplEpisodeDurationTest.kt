package au.com.shiftyjelly.pocketcasts.repositories.sync

import au.com.shiftyjelly.pocketcasts.models.db.AppDatabase
import au.com.shiftyjelly.pocketcasts.models.db.dao.AlternateEnclosureDao
import au.com.shiftyjelly.pocketcasts.models.db.dao.PlaylistDao
import au.com.shiftyjelly.pocketcasts.models.db.dao.PodcastDao
import au.com.shiftyjelly.pocketcasts.models.entity.Podcast
import au.com.shiftyjelly.pocketcasts.models.entity.PodcastEpisode
import au.com.shiftyjelly.pocketcasts.models.type.EpisodeDownloadStatus
import au.com.shiftyjelly.pocketcasts.repositories.playback.PlaybackManager
import au.com.shiftyjelly.pocketcasts.repositories.podcast.EpisodeManager
import au.com.shiftyjelly.pocketcasts.servers.podcast.EpisodeInfo
import au.com.shiftyjelly.pocketcasts.servers.podcast.PodcastCacheServiceManager
import au.com.shiftyjelly.pocketcasts.servers.podcast.PodcastInfo
import au.com.shiftyjelly.pocketcasts.servers.podcast.PodcastResponse
import com.automattic.android.tracks.crashlogging.CrashLogging
import java.util.Date
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import retrofit2.Response

@RunWith(RobolectricTestRunner::class)
class PodcastRefresherImplEpisodeDurationTest {
    private val podcastUuid = "podcast-uuid"
    private val episodeUuid = "episode-uuid"

    private val episodeManager = mock<EpisodeManager>()
    private val cacheServiceManager = mock<PodcastCacheServiceManager>()
    private val playbackManager = mock<PlaybackManager>()
    private val appDatabase = mock<AppDatabase> {
        on { podcastDao() } doReturn mock<PodcastDao>()
        on { playlistDao() } doReturn mock<PlaylistDao>()
        on { alternateEnclosureDao() } doReturn mock<AlternateEnclosureDao>()
    }

    private val refresher = PodcastRefresherImpl(
        episodeManager = episodeManager,
        appDatabase = appDatabase,
        cacheServiceManager = cacheServiceManager,
        crashLogging = mock<CrashLogging>(),
    )

    @Test
    fun `refresh keeps the measured duration of a downloaded episode`() = runTest {
        val existingEpisode = createEpisode(downloadStatus = EpisodeDownloadStatus.Downloaded, duration = MEASURED_DURATION_SECS)

        refresh(existingEpisode, feedDurationSecs = FEED_DURATION_SECS)

        verify(episodeManager).update(argThat { duration == MEASURED_DURATION_SECS })
    }

    @Test
    fun `refresh takes the feed duration for an episode that is not downloaded`() = runTest {
        val existingEpisode = createEpisode(downloadStatus = EpisodeDownloadStatus.DownloadNotRequested, duration = MEASURED_DURATION_SECS)

        refresh(existingEpisode, feedDurationSecs = FEED_DURATION_SECS)

        verify(episodeManager).update(argThat { duration == FEED_DURATION_SECS })
    }

    // The feed title always differs from the stored one so the refresher persists the episode and the duration can be asserted.
    private suspend fun refresh(existingEpisode: PodcastEpisode, feedDurationSecs: Double) {
        val existingPodcast = Podcast(uuid = podcastUuid)
        val feedEpisode = EpisodeInfo(
            uuid = episodeUuid,
            title = "Title from the feed",
            season = null,
            number = null,
            type = null,
            url = "https://example.com/episode.mp3",
            alternateEnclosures = null,
            fileType = "audio/mpeg",
            fileSize = null,
            duration = feedDurationSecs,
            published = "2026-10-26T12:00:00Z",
            slug = null,
            hasGeneratedTranscript = null,
        )
        val feedResponse = PodcastResponse(
            episodeFrequency = "weekly",
            estimatedNextEpisodeAt = null,
            episodeCount = 1,
            hasMoreEpisodes = false,
            hasSeasons = false,
            seasonCount = 0,
            refreshAllowed = false,
            podcastInfo = PodcastInfo(
                uuid = podcastUuid,
                url = null,
                title = null,
                author = null,
                description = null,
                descriptionHtml = null,
                showType = null,
                category = null,
                audio = null,
                episodes = listOf(feedEpisode),
                isPrivate = null,
                fundings = null,
                slug = null,
                explicit = null,
                webFeed = null,
                networkList = null,
            ),
        )
        whenever(cacheServiceManager.getPodcastResponse(podcastUuid)).thenReturn(Response.success(feedResponse))
        whenever(episodeManager.findEpisodesByPodcastOrderedByPublishDate(existingPodcast)).thenReturn(listOf(existingEpisode))

        refresher.refreshPodcast(existingPodcast, playbackManager)
    }

    private fun createEpisode(downloadStatus: EpisodeDownloadStatus, duration: Double) = PodcastEpisode(
        uuid = episodeUuid,
        publishedDate = Date(),
        title = "Title in the database",
        duration = duration,
        downloadStatus = downloadStatus,
        podcastUuid = podcastUuid,
    )

    private companion object {
        // The player measured the file at 53:09 while the feed declares about 44:10.
        const val MEASURED_DURATION_SECS = 3189.0
        const val FEED_DURATION_SECS = 2650.0
    }
}
