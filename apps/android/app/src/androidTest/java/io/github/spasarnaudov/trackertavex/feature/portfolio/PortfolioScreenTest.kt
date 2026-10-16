package io.github.spasarnaudov.trackertavex.feature.portfolio

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.spasarnaudov.trackertavex.core.data.AssetsRepository
import io.github.spasarnaudov.trackertavex.core.data.PortfolioRepository
import io.github.spasarnaudov.trackertavex.testutil.FakeTokenStorage
import io.github.spasarnaudov.trackertavex.testutil.TestApiServiceFactory
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Provider

class PortfolioScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var server: MockWebServer
    private lateinit var portfolioRepository: PortfolioRepository
    private lateinit var assetsRepository: AssetsRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val tokenStorage = FakeTokenStorage(initialToken = "token")
        val apiService = TestApiServiceFactory.create(server.url("/").toString(), tokenStorage)
        portfolioRepository = PortfolioRepository(Provider { apiService })
        assetsRepository = AssetsRepository(Provider { apiService })
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun setContent(onAddManualItem: () -> Unit = {}) {
        composeTestRule.setContent {
            PortfolioScreen(
                onAddManualItem = onAddManualItem,
                onEditManualItem = {},
                onOpenPurchases = { _, _, _ -> },
                viewModel = PortfolioViewModel(portfolioRepository, assetsRepository),
            )
        }
    }

    private fun enqueueEmptyHistoryResponse() {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody("""{"range":"1m","interval":"daily","points":[]}"""),
        )
    }

    @Test
    fun contentStateShowsHoldingsAndTotalValue() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"assets":[],"gold_buyback_assets":[]}"""))
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "holdings": [{"asset_id": 12, "asset_symbol": "BTC", "asset_name": "Bitcoin", "quantity": 2.5, "include_in_chart": true, "price": 100, "value": 250}],
                  "manual_items": [],
                  "total_value": 250
                }
                """.trimIndent(),
            ),
        )
        enqueueEmptyHistoryResponse()

        setContent()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTextContaining("BTC").isNotEmpty()
        }
        composeTestRule.onNodeWithText("Total value: 250.00").assertIsDisplayed()
    }

    @Test
    fun emptyStateShowsAddManualItemAction() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"assets":[],"gold_buyback_assets":[]}"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"holdings":[],"manual_items":[]}"""))
        enqueueEmptyHistoryResponse()

        var tapped = false
        setContent(onAddManualItem = { tapped = true })

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTextContaining("Add manual item").isNotEmpty()
        }
        composeTestRule.onNodeWithText("Add manual item").performClick()
        assert(tapped)
    }

    @Test
    fun errorStateShowsRetryAndRetryReloads() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"assets":[],"gold_buyback_assets":[]}"""))
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"error":{"code":"internal","message":"Server error."}}"""))
        enqueueEmptyHistoryResponse()

        setContent()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTextContaining("Retry").isNotEmpty()
        }
        composeTestRule.onNodeWithText("Server error.").assertIsDisplayed()

        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"assets":[],"gold_buyback_assets":[]}"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"holdings":[],"manual_items":[]}"""))
        enqueueEmptyHistoryResponse()
        composeTestRule.onNodeWithText("Retry").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTextContaining("Add manual item").isNotEmpty()
        }
    }
}

private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextContaining(text: String) =
    onAllNodesWithText(text, substring = true).fetchSemanticsNodes()
