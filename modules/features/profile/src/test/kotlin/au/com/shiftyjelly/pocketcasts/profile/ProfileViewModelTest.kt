package au.com.shiftyjelly.pocketcasts.profile

import au.com.shiftyjelly.pocketcasts.analytics.testing.TestEventSink
import au.com.shiftyjelly.pocketcasts.models.to.RefreshState
import au.com.shiftyjelly.pocketcasts.models.type.SignInState
import au.com.shiftyjelly.pocketcasts.models.type.Subscription
import au.com.shiftyjelly.pocketcasts.preferences.Settings
import au.com.shiftyjelly.pocketcasts.preferences.UserSetting
import au.com.shiftyjelly.pocketcasts.repositories.endofyear.EndOfYearManager
import au.com.shiftyjelly.pocketcasts.repositories.podcast.PodcastManager
import au.com.shiftyjelly.pocketcasts.repositories.user.StatsManager
import au.com.shiftyjelly.pocketcasts.repositories.user.UserManager
import au.com.shiftyjelly.pocketcasts.repositories.whatsnew.WhatsNewManager
import au.com.shiftyjelly.pocketcasts.sharedtest.MainCoroutineRule
import com.automattic.eventhorizon.EventHorizon
import io.reactivex.Flowable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ProfileViewModelTest {
    @get:Rule
    val coroutineRule = MainCoroutineRule()

    private val upgradeProfileClosedSetting = mock<UserSetting<Boolean>> {
        on { flow } doReturn MutableStateFlow(false)
    }
    private val freeAccountBannerDismissedSetting = mock<UserSetting<Boolean>> {
        on { flow } doReturn MutableStateFlow(false)
    }
    private val settings = mock<Settings> {
        on { upgradeProfileClosed } doReturn upgradeProfileClosedSetting
        on { isFreeAccountProfileBannerDismissed } doReturn freeAccountBannerDismissedSetting
        on { refreshStateFlow } doReturn MutableStateFlow<RefreshState>(RefreshState.Never)
        on { bottomInset } doReturn flowOf(0)
    }
    private val userManager = mock<UserManager>()
    private val podcastManager = mock<PodcastManager> {
        on { countSubscribedFlow() } doReturn flowOf(0)
    }
    private val whatsNewManager = mock<WhatsNewManager> {
        on { hasUnlistedMessages } doReturn flowOf(false)
    }

    private fun createViewModel(signInState: SignInState) = run {
        whenever(userManager.getSignInState()).thenReturn(Flowable.just(signInState))
        ProfileViewModel(
            settings = settings,
            podcastManager = podcastManager,
            statsManager = mock<StatsManager>(),
            userManager = userManager,
            endOfYearManager = mock<EndOfYearManager>(),
            eventHorizon = EventHorizon(TestEventSink()),
            whatsNewManager = whatsNewManager,
        )
    }

    @Test
    fun `upgrade banner is hidden for signed out users`() = runBlocking {
        val viewModel = createViewModel(SignInState.SignedOut)

        assertFalse(viewModel.showUpgradeBanner.first())
    }

    @Test
    fun `upgrade banner is hidden for free signed in users who have not closed it`() = runBlocking {
        val viewModel = createViewModel(SignInState.SignedIn(email = "user@example.com", subscription = null))

        assertFalse(viewModel.showUpgradeBanner.first())
    }

    @Test
    fun `upgrade banner is hidden for plus users`() = runBlocking {
        val viewModel = createViewModel(
            SignInState.SignedIn(email = "user@example.com", subscription = Subscription.PlusPreview),
        )

        assertFalse(viewModel.showUpgradeBanner.first())
    }
}
