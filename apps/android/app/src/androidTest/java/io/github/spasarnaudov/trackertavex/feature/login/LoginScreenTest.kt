package io.github.spasarnaudov.trackertavex.feature.login

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import io.github.spasarnaudov.trackertavex.core.auth.SessionManager
import io.github.spasarnaudov.trackertavex.core.data.AuthRepository
import io.github.spasarnaudov.trackertavex.testutil.FakeTokenStorage
import io.github.spasarnaudov.trackertavex.testutil.TestApiServiceFactory
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Provider

class LoginScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var server: MockWebServer
    private lateinit var authRepository: AuthRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val tokenStorage = FakeTokenStorage()
        val apiService = TestApiServiceFactory.create(server.url("/").toString(), tokenStorage)
        authRepository = AuthRepository(Provider { apiService }, tokenStorage, SessionManager())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun submittingAnEmptyFormShowsValidationErrors() {
        composeTestRule.setContent {
            LoginScreen(
                onLoginSuccess = {},
                onNavigateToRegister = {},
                onNavigateToConnectionSettings = {},
                viewModel = LoginViewModel(authRepository),
            )
        }

        composeTestRule.onNodeWithText("Sign in").performClick()

        composeTestRule.onNodeWithText("Username is required.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Password is required.").assertIsDisplayed()
    }

    @Test
    fun successfulLoginInvokesTheSuccessCallback() {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"token":"abc","user":{"id":1,"username":"demo","role":"user"}}""",
            ),
        )
        var succeeded = false

        composeTestRule.setContent {
            LoginScreen(
                onLoginSuccess = { succeeded = true },
                onNavigateToRegister = {},
                onNavigateToConnectionSettings = {},
                viewModel = LoginViewModel(authRepository),
            )
        }

        composeTestRule.onNodeWithText("Username").performTextInput("demo")
        composeTestRule.onNodeWithText("Password").performTextInput("strong-password")
        composeTestRule.onNodeWithText("Sign in").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) { succeeded }
        assertTrue(succeeded)
    }

    @Test
    fun activeSessionConflictShowsConfirmationDialogAndForceLoginOnContinue() {
        server.enqueue(
            MockResponse().setResponseCode(409).setBody(
                """{"error":{"code":"active_session","message":"This account already has an active session."}}""",
            ),
        )
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"token":"forced","user":{"id":1,"username":"demo","role":"user"}}""",
            ),
        )
        var succeeded = false

        composeTestRule.setContent {
            LoginScreen(
                onLoginSuccess = { succeeded = true },
                onNavigateToRegister = {},
                onNavigateToConnectionSettings = {},
                viewModel = LoginViewModel(authRepository),
            )
        }

        composeTestRule.onNodeWithText("Username").performTextInput("demo")
        composeTestRule.onNodeWithText("Password").performTextInput("strong-password")
        composeTestRule.onNodeWithText("Sign in").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("Continue").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Cancel").assertIsDisplayed()
        composeTestRule.onNodeWithText("Continue").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) { succeeded }
        assertEquals(2, server.requestCount)
    }
}
