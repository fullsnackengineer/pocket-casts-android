package au.com.shiftyjelly.pocketcasts.repositories.playback

import au.com.shiftyjelly.pocketcasts.models.entity.BaseEpisode
import au.com.shiftyjelly.pocketcasts.models.entity.PodcastEpisode
import au.com.shiftyjelly.pocketcasts.models.type.EpisodePlayingStatus
import au.com.shiftyjelly.pocketcasts.models.type.UpNextSortType
import au.com.shiftyjelly.pocketcasts.preferences.model.AutoPlaySource
import io.reactivex.Observable
import io.reactivex.subjects.BehaviorSubject
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(manifest = Config.NONE)
@RunWith(RobolectricTestRunner::class)
class UpNextQueueUpdateCurrentEpisodeStateIfNeededTest {

    @Test
    fun `reconciles cache when playedUpTo differs from the database`() {
        val cachedEpisode = createPodcastEpisode(playedUpToMs = 0)
        val upNextQueue = FakeUpNextQueue(cachedEpisode)

        val episodeFromDb = createPodcastEpisode(playedUpToMs = 20 * 60 * 1000)
        val newState = UpNextQueue.State.Loaded(episodeFromDb, podcast = null, queue = emptyList())

        upNextQueue.updateCurrentEpisodeStateIfNeeded(episodeFromDb, newState)

        assertEquals(1, upNextQueue.updateCount)
        assertEquals(episodeFromDb.playedUpToMs, upNextQueue.currentEpisode?.playedUpToMs)
    }

    @Test
    fun `reconciles cache when isStarred differs from the database`() {
        val cachedEpisode = createPodcastEpisode(isStarred = false)
        val upNextQueue = FakeUpNextQueue(cachedEpisode)

        val episodeFromDb = createPodcastEpisode(isStarred = true)
        val newState = UpNextQueue.State.Loaded(episodeFromDb, podcast = null, queue = emptyList())

        upNextQueue.updateCurrentEpisodeStateIfNeeded(episodeFromDb, newState)

        assertEquals(1, upNextQueue.updateCount)
    }

    @Test
    fun `reconciles cache when duration differs from the database`() {
        val cachedEpisode = createPodcastEpisode(duration = 0.0)
        val upNextQueue = FakeUpNextQueue(cachedEpisode)

        val episodeFromDb = createPodcastEpisode(duration = 1800.0)
        val newState = UpNextQueue.State.Loaded(episodeFromDb, podcast = null, queue = emptyList())

        upNextQueue.updateCurrentEpisodeStateIfNeeded(episodeFromDb, newState)

        assertEquals(1, upNextQueue.updateCount)
    }

    @Test
    fun `reconciles cache when isArchived differs from the database`() {
        val cachedEpisode = createPodcastEpisode(isArchived = false)
        val upNextQueue = FakeUpNextQueue(cachedEpisode)

        val episodeFromDb = createPodcastEpisode(isArchived = true)
        val newState = UpNextQueue.State.Loaded(episodeFromDb, podcast = null, queue = emptyList())

        upNextQueue.updateCurrentEpisodeStateIfNeeded(episodeFromDb, newState)

        assertEquals(1, upNextQueue.updateCount)
    }

    @Test
    fun `reconciles cache when playingStatus differs from the database`() {
        val cachedEpisode = createPodcastEpisode()
        val upNextQueue = FakeUpNextQueue(cachedEpisode)

        val episodeFromDb = createPodcastEpisode().apply {
            playingStatus = EpisodePlayingStatus.COMPLETED
        }
        val newState = UpNextQueue.State.Loaded(episodeFromDb, podcast = null, queue = emptyList())

        upNextQueue.updateCurrentEpisodeStateIfNeeded(episodeFromDb, newState)

        assertEquals(1, upNextQueue.updateCount)
    }

    @Test
    fun `does not update cache when nothing relevant changed`() {
        val cachedEpisode = createPodcastEpisode(playedUpToMs = 60_000, isStarred = true, duration = 1800.0, isArchived = false)
        val upNextQueue = FakeUpNextQueue(cachedEpisode)

        val episodeFromDb = createPodcastEpisode(playedUpToMs = 60_000, isStarred = true, duration = 1800.0, isArchived = false)
        val sameState = UpNextQueue.State.Loaded(episodeFromDb, podcast = null, queue = emptyList())

        upNextQueue.updateCurrentEpisodeStateIfNeeded(episodeFromDb, sameState)

        assertEquals(0, upNextQueue.updateCount)
    }

    @Test
    fun `does not update cache for a different episode`() {
        val cachedEpisode = createPodcastEpisode(uuid = "current-episode", playedUpToMs = 0)
        val upNextQueue = FakeUpNextQueue(cachedEpisode)

        val otherEpisodeFromDb = createPodcastEpisode(uuid = "some-other-episode", playedUpToMs = 60_000)
        val newState = UpNextQueue.State.Loaded(otherEpisodeFromDb, podcast = null, queue = emptyList())

        upNextQueue.updateCurrentEpisodeStateIfNeeded(otherEpisodeFromDb, newState)

        assertEquals(0, upNextQueue.updateCount)
    }

    private fun createPodcastEpisode(
        uuid: String = "episode-uuid",
        playedUpToMs: Int = 0,
        isStarred: Boolean = false,
        duration: Double = 0.0,
        isArchived: Boolean = false,
    ): PodcastEpisode {
        return PodcastEpisode(
            uuid = uuid,
            title = "Test Episode",
            publishedDate = Date(),
            podcastUuid = "podcast-uuid",
        ).apply {
            this.playedUpToMs = playedUpToMs
            this.isStarred = isStarred
            this.duration = duration
            this.isArchived = isArchived
        }
    }

    /**
     * updateCurrentEpisodeStateIfNeeded/currentEpisode/updateCurrentEpisodeState are the only
     * members exercised here (all default/abstract members on UpNextQueue used by the default
     * method under test) - everything else on the interface is irrelevant to this test.
     */
    private class FakeUpNextQueue(initialEpisode: BaseEpisode) : UpNextQueue {
        private val stateSubject = BehaviorSubject.createDefault<UpNextQueue.State>(
            UpNextQueue.State.Loaded(initialEpisode, podcast = null, queue = emptyList()),
        )

        var updateCount = 0
            private set

        override val isEmpty: Boolean get() = false
        override val changesObservable: Observable<UpNextQueue.State> = stateSubject
        override val currentEpisode: BaseEpisode?
            get() = (stateSubject.value as? UpNextQueue.State.Loaded)?.episode
        override val queueEpisodes: List<BaseEpisode> get() = emptyList()

        override fun updateCurrentEpisodeState(state: UpNextQueue.State) {
            updateCount++
            stateSubject.onNext(state)
        }

        override fun isCurrentEpisode(episode: BaseEpisode) = episode.uuid == currentEpisode?.uuid
        override suspend fun playNow(episode: BaseEpisode, automaticUpNextSource: AutoPlaySource?, isUserInitiated: Boolean, onAdd: (() -> Unit)?) = Unit
        override suspend fun playNextBlocking(episode: BaseEpisode, isUserInitiated: Boolean, onAdd: (() -> Unit)?) = Unit
        override suspend fun playLast(episode: BaseEpisode, isUserInitiated: Boolean, onAdd: (() -> Unit)?) = Unit
        override suspend fun playAllNext(episodes: List<BaseEpisode>, isUserInitiated: Boolean) = Unit
        override suspend fun playAllLast(episodes: List<BaseEpisode>, isUserInitiated: Boolean) = Unit
        override suspend fun removeEpisode(episode: BaseEpisode, shouldShuffleUpNext: Boolean) = Unit
        override suspend fun clearAndPlayAll(episodes: List<BaseEpisode>, isUserInitiated: Boolean) = Unit
        override fun moveEpisode(from: Int, to: Int) = Unit
        override fun changeList(episodes: List<BaseEpisode>) = Unit
        override fun clearUpNext() = Unit
        override fun removeAll() = Unit
        override suspend fun removeAllIncludingChanges() = Unit
        override suspend fun importServerChangesBlocking(episodes: List<BaseEpisode>, playbackManager: PlaybackManager) = Unit
        override fun contains(uuid: String) = false
        override fun sortUpNext(sortType: UpNextSortType) = Unit
        override fun setupBlocking() = Unit
    }
}
