package io.github.spasarnaudov.trackertavex.feature.account

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.spasarnaudov.trackertavex.core.auth.SessionManager
import io.github.spasarnaudov.trackertavex.core.data.AccountRepository
import io.github.spasarnaudov.trackertavex.core.data.AuthRepository
import io.github.spasarnaudov.trackertavex.testutil.FakeTokenStorage
import io.github.spasarnaudov.trackertavex.testutil.TestApiServiceFactory
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Provider

class AccountScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var server: MockWebServer
    private lateinit var accountRepository: AccountRepository
    private lateinit var authRepository: AuthRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val tokenStorage = FakeTokenStorage(initialToken = "token")
        val sessionManager = SessionManager()
        val apiService = TestApiServiceFactory.create(server.url("/").toString(), tokenStorage, sessionManager)
        accountRepository = AccountRepository(Provider { apiService }, tokenStorage, sessionManager)
        authRepository = AuthRepository(Provider { apiService }, tokenStorage, sessionManager)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun setContent(onLoggedOut: () -> Unit = {}) {
        composeTestRule.setContent {
            AccountScreen(
                onLoggedOut = onLoggedOut,
                onChangePassword = {},
                onDeleteAccount = {},
                onConnectionSettings = {},
                viewModel = AccountViewModel(accountRepository, authRepository),
            )
        }
    }

    @Test
    fun showsNoAdminSectionForARegularUser() {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody("""{"id":1,"username":"demo","role":"user"}"""),
        )

        setContent()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("Log out").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onAllNodesWithText("Users").assertCountEquals(0)
    }

    @Test
    fun showsNoAdminSectionForAnAdmin() {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody("""{"id":1,"username":"root","role":"admin"}"""),
        )

        setContent()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("Log out").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onAllNodesWithText("Users").assertCountEquals(0)
    }

    @Test
    fun tappingLogOutClearsTheSessionAndInvokesTheCallback() {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody("""{"id":1,"username":"demo","role":"user"}"""),
        )
        server.enqueue(MockResponse().setResponseCode(200))
        var loggedOut = false

        setContent(onLoggedOut = { loggedOut = true })

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("Log out").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Log out").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) { loggedOut }
    }
}
