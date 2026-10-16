package io.github.spasarnaudov.trackertavex.core.data

import io.github.spasarnaudov.trackertavex.core.model.AppError
import io.github.spasarnaudov.trackertavex.core.model.AssetPriceInterval
import io.github.spasarnaudov.trackertavex.core.model.ChartRange
import io.github.spasarnaudov.trackertavex.core.network.ApiResult
import io.github.spasarnaudov.trackertavex.testutil.FakeTokenStorage
import io.github.spasarnaudov.trackertavex.testutil.TestApiServiceFactory
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import javax.inject.Provider

class AssetsRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: AssetsRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val apiService = TestApiServiceFactory.create(server.url("/").toString(), FakeTokenStorage())
        repository = AssetsRepository(Provider { apiService })
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `getAssets decodes both the assets and gold_buyback_assets lists`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "assets": [{"id": 1, "symbol": "BTC", "name": "Bitcoin"}],
                  "gold_buyback_assets": [{"id": 31, "symbol": "XAU", "name": "Gold"}]
                }
                """.trimIndent(),
            ),
        )

        val result = repository.getAssets()

        assertTrue(result is ApiResult.Success)
        val catalog = (result as ApiResult.Success).data
        assertEquals(1, catalog.assets.size)
        assertEquals("BTC", catalog.assets.first().symbol)
        assertEquals(1, catalog.goldBuybackAssets.size)
        assertTrue(catalog.goldBuybackAssets.first().isGoldBuyback)
    }

    @Test
    fun `getAssetPrices sends range and interval as query parameters`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))

        repository.getAssetPrices(assetId = 31, range = ChartRange.ONE_MONTH, interval = AssetPriceInterval.DAILY)

        val recorded = server.takeRequest()
        assertTrue(recorded.path!!.startsWith("/assets/31/prices"))
        assertTrue(recorded.path!!.contains("range=1m"))
        assertTrue(recorded.path!!.contains("interval=daily"))
    }

    @Test
    fun `getAssetPrices with a custom range sends start_date and end_date`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))

        repository.getAssetPrices(
            assetId = 31,
            range = ChartRange.CUSTOM,
            interval = AssetPriceInterval.DAILY,
            startDate = LocalDate.of(2026, 1, 1),
            endDate = LocalDate.of(2026, 1, 31),
        )

        val recorded = server.takeRequest()
        assertTrue(recorded.path!!.contains("range=custom"))
        assertTrue(recorded.path!!.contains("start_date=2026-01-01"))
        assertTrue(recorded.path!!.contains("end_date=2026-01-31"))
    }

    @Test
    fun `getAssetPrices with a custom range and missing dates is rejected locally`() = runTest {
        val result = repository.getAssetPrices(assetId = 31, range = ChartRange.CUSTOM, interval = AssetPriceInterval.DAILY)

        assertTrue(result is ApiResult.Error)
        assertTrue((result as ApiResult.Error).error is AppError.BadRequest)
        assertEquals(0, server.requestCount)
    }
}
