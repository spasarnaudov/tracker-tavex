package io.github.spasarnaudov.trackertavex.core.data

import android.content.ContentResolver
import android.net.Uri
import io.github.spasarnaudov.trackertavex.core.model.ChartRange
import io.github.spasarnaudov.trackertavex.core.model.Holding
import io.github.spasarnaudov.trackertavex.core.model.ManualItem
import io.github.spasarnaudov.trackertavex.core.model.PortfolioHistoryInterval
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
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.ByteArrayInputStream
import java.math.BigDecimal
import javax.inject.Provider

class PortfolioRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: PortfolioRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val apiService = TestApiServiceFactory.create(server.url("/").toString(), FakeTokenStorage())
        repository = PortfolioRepository(Provider { apiService })
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `update portfolio maps holdings and a new manual item with id null`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"holdings":[],"manual_items":[]}"""))

        val holdings = listOf(
            Holding(assetId = 12, assetSymbol = "BTC", assetName = "Bitcoin", quantity = BigDecimal("2.5"), includeInChart = true, price = null, value = null),
        )
        val manualItems = listOf(
            ManualItem(id = null, name = "Gold ring", quantity = BigDecimal("8.2"), unitPrice = BigDecimal("70"), priceAssetId = null, includeInChart = true, value = null),
        )

        repository.updatePortfolio(holdings, manualItems)

        // Field names on the wire are snake_case, matching API.md's PUT /portfolio example exactly.
        // Holding quantity is server-computed from purchase lots, so only asset_id/include_in_chart go out.
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"asset_id\":12"))
        assertTrue(body.contains("\"include_in_chart\":true"))
        assertTrue(body.contains("\"id\":null"))
        assertTrue(body.contains("\"name\":\"Gold ring\""))
        assertTrue(body.contains("\"unit_price\":70"))
    }

    @Test
    fun `update portfolio marks an existing manual item for deletion`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"holdings":[],"manual_items":[]}"""))

        val manualItems = listOf(
            ManualItem(id = 7, name = "Old item", quantity = BigDecimal.ONE, unitPrice = null, priceAssetId = null, includeInChart = true, value = null, markedForDeletion = true),
        )

        repository.updatePortfolio(emptyList(), manualItems)

        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"id\":7"))
        assertTrue(body.contains("\"delete\":true"))
    }

    @Test
    fun `update portfolio with a negative manual item quantity is rejected locally without an HTTP call`() = runTest {
        val manualItems = listOf(
            ManualItem(id = null, name = "Gold ring", quantity = BigDecimal("-1"), unitPrice = BigDecimal("70"), priceAssetId = null, includeInChart = true, value = null),
        )

        val result = repository.updatePortfolio(emptyList(), manualItems)

        assertTrue(result is ApiResult.Error)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `get portfolio history sends the requested range and interval as query params`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))

        repository.getPortfolioHistory(ChartRange.ONE_WEEK, PortfolioHistoryInterval.HOURLY)

        val recorded = server.takeRequest()
        assertTrue(recorded.path!!.contains("range=1w"))
        assertTrue(recorded.path!!.contains("interval=hourly"))
    }

    @Test
    fun `upload purchase receipt sends the photo bytes as multipart form data`() = runTest {
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"has_receipt":true}"""))
        val uri = mock<Uri>()
        val contentResolver = mock<ContentResolver>()
        val bytes = "fake-image-bytes".toByteArray()
        whenever(contentResolver.openInputStream(uri)).thenReturn(ByteArrayInputStream(bytes))
        whenever(contentResolver.getType(uri)).thenReturn("image/png")

        val result = repository.uploadPurchaseReceipt(1, uri, contentResolver)

        assertTrue(result is ApiResult.Success)
        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertTrue(recorded.path!!.endsWith("/portfolio/purchases/1/receipt"))
        assertTrue(recorded.headers["Content-Type"]?.contains("multipart/form-data") == true)
        assertTrue(recorded.body.readUtf8().contains("fake-image-bytes"))
    }

    @Test
    fun `upload purchase receipt maps a 404 response to an error`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(404)
                .setBody("""{"error":{"code":"purchase_not_found","message":"Purchase not found."}}"""),
        )
        val uri = mock<Uri>()
        val contentResolver = mock<ContentResolver>()
        whenever(contentResolver.openInputStream(uri)).thenReturn(ByteArrayInputStream(ByteArray(0)))

        val result = repository.uploadPurchaseReceipt(1, uri, contentResolver)

        assertTrue(result is ApiResult.Error)
    }

    @Test
    fun `upload purchase receipt fails locally when the photo cannot be read`() = runTest {
        val uri = mock<Uri>()
        val contentResolver = mock<ContentResolver>()
        whenever(contentResolver.openInputStream(uri)).thenReturn(null)

        val result = repository.uploadPurchaseReceipt(1, uri, contentResolver)

        assertTrue(result is ApiResult.Error)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `delete purchase receipt calls the delete endpoint`() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))

        val result = repository.deletePurchaseReceipt(1)

        assertTrue(result is ApiResult.Success)
        val recorded = server.takeRequest()
        assertEquals("DELETE", recorded.method)
        assertTrue(recorded.path!!.endsWith("/portfolio/purchases/1/receipt"))
    }
}
