package au.com.shiftyjelly.pocketcasts.repositories.playback

import au.com.shiftyjelly.pocketcasts.models.entity.PodcastEpisode
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Robolectric because loading MediaSessionManager.kt builds an Android Bundle in a top-level initializer.
@RunWith(RobolectricTestRunner::class)
class MediaSessionMetadataDurationTest {
    private val episode = PodcastEpisode(uuid = "episode-uuid", publishedDate = Date(), duration = FEED_DURATION_SECS)

    @Test
    fun `uses the player duration when it is for the same episode`() {
        val playbackState = PlaybackState(episodeUuid = "episode-uuid", durationMs = PLAYER_DURATION_MS)

        assertEquals(PLAYER_DURATION_MS.toLong(), metadataDurationMs(episode, playbackState))
    }

    @Test
    fun `uses the episode duration when the player has no duration yet`() {
        val playbackState = PlaybackState(episodeUuid = "episode-uuid", durationMs = -1)

        assertEquals(episode.durationMs.toLong(), metadataDurationMs(episode, playbackState))
    }

    @Test
    fun `uses the episode duration when the player duration is zero`() {
        val playbackState = PlaybackState(episodeUuid = "episode-uuid", durationMs = 0)

        assertEquals(episode.durationMs.toLong(), metadataDurationMs(episode, playbackState))
    }

    @Test
    fun `uses the episode duration when the player is on a different episode`() {
        val playbackState = PlaybackState(episodeUuid = "other-episode-uuid", durationMs = PLAYER_DURATION_MS)

        assertEquals(episode.durationMs.toLong(), metadataDurationMs(episode, playbackState))
    }

    private companion object {
        // The feed declares about 44:10 while the player measured the file at 53:09.
        const val FEED_DURATION_SECS = 2650.0
        const val PLAYER_DURATION_MS = 3_189_000
    }
}
