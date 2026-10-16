package io.github.spasarnaudov.trackertavex.core.network

import io.github.spasarnaudov.trackertavex.core.auth.SessionExpiryNotifier
import io.github.spasarnaudov.trackertavex.core.auth.SessionManager
import io.github.spasarnaudov.trackertavex.testutil.FakeTokenStorage
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class AuthInterceptorTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `attaches Authorization header when a token is stored`() = runTest {
        val tokenStorage = FakeTokenStorage(initialToken = "secret-token")
        val apiService = io.github.spasarnaudov.trackertavex.testutil.TestApiServiceFactory.create(
            server.url("/").toString(),
            tokenStorage,
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":"ok"}"""))

        apiService.health()

        val recorded = server.takeRequest()
        assertEquals("Bearer secret-token", recorded.getHeader("Authorization"))
    }

    @Test
    fun `omits Authorization header when no token is stored`() = runTest {
        val tokenStorage = FakeTokenStorage(initialToken = null)
        val apiService = io.github.spasarnaudov.trackertavex.testutil.TestApiServiceFactory.create(
            server.url("/").toString(),
            tokenStorage,
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":"ok"}"""))

        apiService.health()

        val recorded = server.takeRequest()
        assertNull(recorded.getHeader("Authorization"))
    }

    @Test
    fun `401 on an authenticated request clears the token and notifies session expiry`() = runTest {
        val tokenStorage = FakeTokenStorage(initialToken = "secret-token")
        val sessionManager = SessionManager()
        val notifier = SessionExpiryNotifier()
        val apiService = io.github.spasarnaudov.trackertavex.testutil.TestApiServiceFactory.create(
            server.url("/").toString(),
            tokenStorage,
            sessionManager,
            notifier,
        )
        server.enqueue(
            MockResponse().setResponseCode(401)
                .setBody("""{"error":{"code":"invalid_token","message":"Expired"}}"""),
        )

        apiService.getCurrentUser()

        assertNull(tokenStorage.getToken())
        assertEquals(1, tokenStorage.clearCallCount)
        assertNull(sessionManager.currentUser.value)
    }

    @Test
    fun `401 on a public unauthenticated request does not clear a token or notify`() = runTest {
        val tokenStorage = FakeTokenStorage(initialToken = null)
        val apiService = io.github.spasarnaudov.trackertavex.testutil.TestApiServiceFactory.create(
            server.url("/").toString(),
            tokenStorage,
        )
        server.enqueue(
            MockResponse().setResponseCode(401)
                .setBody("""{"error":{"code":"invalid_credentials","message":"Bad login"}}"""),
        )

        apiService.login(io.github.spasarnaudov.trackertavex.core.network.dto.LoginRequestDto("demo", "wrong"))

        assertEquals(0, tokenStorage.clearCallCount)
    }

    @Test
    fun `403 does not clear the token`() = runTest {
        val tokenStorage = FakeTokenStorage(initialToken = "secret-token")
        val apiService = io.github.spasarnaudov.trackertavex.testutil.TestApiServiceFactory.create(
            server.url("/").toString(),
            tokenStorage,
        )
        server.enqueue(
            MockResponse().setResponseCode(403)
                .setBody("""{"error":{"code":"forbidden","message":"Not allowed"}}"""),
        )

        val response = apiService.getAdminUsers()

        assertFalse(response.isSuccessful)
        assertEquals(403, response.code())
        assertEquals("secret-token", tokenStorage.getToken())
        assertEquals(0, tokenStorage.clearCallCount)
    }
}
