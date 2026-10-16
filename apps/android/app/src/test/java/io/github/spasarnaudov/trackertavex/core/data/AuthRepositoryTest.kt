package io.github.spasarnaudov.trackertavex.core.data

import io.github.spasarnaudov.trackertavex.core.auth.SessionManager
import io.github.spasarnaudov.trackertavex.core.model.AppError
import io.github.spasarnaudov.trackertavex.core.network.ApiResult
import io.github.spasarnaudov.trackertavex.testutil.FakeTokenStorage
import io.github.spasarnaudov.trackertavex.testutil.TestApiServiceFactory
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import javax.inject.Provider

class AuthRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var tokenStorage: FakeTokenStorage
    private lateinit var sessionManager: SessionManager
    private lateinit var repository: AuthRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        tokenStorage = FakeTokenStorage()
        sessionManager = SessionManager()
        val apiService = TestApiServiceFactory.create(server.url("/").toString(), tokenStorage, sessionManager)
        repository = AuthRepository(Provider { apiService }, tokenStorage, sessionManager)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `login success stores the token and the current user`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"token":"abc123","token_type":"Bearer","expires_at":"2026-07-19T18:30:00","user":{"id":2,"username":"demo","role":"user"}}""",
            ),
        )

        val result = repository.login("demo", "strong-password")

        assertTrue(result is ApiResult.Success)
        assertEquals("demo", (result as ApiResult.Success).data.username)
        assertEquals("abc123", tokenStorage.getToken())
        assertEquals("demo", sessionManager.currentUser.value?.username)
    }

    @Test
    fun `login with invalid credentials surfaces the error and stores no token`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(401).setBody(
                """{"error":{"code":"invalid_credentials","message":"Invalid username or password."}}""",
            ),
        )

        val result = repository.login("demo", "wrong-password")

        assertTrue(result is ApiResult.Error)
        assertTrue((result as ApiResult.Error).error is AppError.Unauthorized)
        assertNull(tokenStorage.getToken())
    }

    @Test
    fun `login returns an active session conflict without setting force`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(409).setBody(
                """{"error":{"code":"active_session","message":"Already logged in elsewhere."}}""",
            ),
        )

        val result = repository.login("demo", "strong-password")

        assertTrue(result is ApiResult.Error)
        assertTrue((result as ApiResult.Error).error is AppError.ActiveSessionConflict)

        val recorded = server.takeRequest()
        assertTrue(!recorded.body.readUtf8().contains("\"force\":true"))
    }

    @Test
    fun `force login sends force true and succeeds`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"token":"forced-token","user":{"id":2,"username":"demo","role":"user"}}""",
            ),
        )

        val result = repository.login("demo", "strong-password", force = true)

        assertTrue(result is ApiResult.Success)
        val recorded = server.takeRequest()
        assertTrue(recorded.body.readUtf8().contains("\"force\":true"))
        assertEquals("forced-token", tokenStorage.getToken())
    }

    @Test
    fun `restoreSession with a 401 clears the token and the session`() = runTest {
        tokenStorage.saveToken("stale-token", null)
        sessionManager.setUser(io.github.spasarnaudov.trackertavex.core.model.User(1, "demo", "user"))
        server.enqueue(
            MockResponse().setResponseCode(401).setBody(
                """{"error":{"code":"invalid_token","message":"Expired"}}""",
            ),
        )

        val result = repository.restoreSession()

        assertTrue(result is ApiResult.Error)
        assertNull(tokenStorage.getToken())
        assertNull(sessionManager.currentUser.value)
    }

    @Test
    fun `restoreSession without a stored token does not call the network`() = runTest {
        val result = repository.restoreSession()

        assertTrue(result is ApiResult.Error)
        assertTrue((result as ApiResult.Error).error is AppError.Unauthorized)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `logout always clears local state even if the server call fails`() = runTest {
        tokenStorage.saveToken("token", null)
        sessionManager.setUser(io.github.spasarnaudov.trackertavex.core.model.User(1, "demo", "user"))
        server.enqueue(MockResponse().setResponseCode(500))

        repository.logout()

        assertNull(tokenStorage.getToken())
        assertNull(sessionManager.currentUser.value)
    }
}
