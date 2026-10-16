package io.github.spasarnaudov.trackertavex.core.network

import io.github.spasarnaudov.trackertavex.core.network.dto.AssetPurchaseRequestDto
import io.github.spasarnaudov.trackertavex.core.network.dto.AssetPurchaseResponseDto
import io.github.spasarnaudov.trackertavex.core.network.dto.AssetPurchasesResponseDto
import io.github.spasarnaudov.trackertavex.core.network.dto.AssetsResponseDto
import io.github.spasarnaudov.trackertavex.core.network.dto.ChartConfigurationDto
import io.github.spasarnaudov.trackertavex.core.network.dto.HealthResponseDto
import io.github.spasarnaudov.trackertavex.core.network.dto.LoginRequestDto
import io.github.spasarnaudov.trackertavex.core.network.dto.LoginResponseDto
import io.github.spasarnaudov.trackertavex.core.network.dto.PortfolioHistoryPointDto
import io.github.spasarnaudov.trackertavex.core.network.dto.PortfolioHistoryResponseDto
import io.github.spasarnaudov.trackertavex.core.network.dto.PortfolioResponseDto
import io.github.spasarnaudov.trackertavex.core.network.dto.PortfolioUpdateRequestDto
import io.github.spasarnaudov.trackertavex.core.network.dto.PricePointDto
import io.github.spasarnaudov.trackertavex.core.network.dto.AssetPricesResponseDto
import io.github.spasarnaudov.trackertavex.core.network.dto.RegisterRequestDto
import io.github.spasarnaudov.trackertavex.core.network.dto.UserDto
import io.github.spasarnaudov.trackertavex.core.network.dto.SessionResponseDto
import io.github.spasarnaudov.trackertavex.core.network.dto.AdminUserDto
import io.github.spasarnaudov.trackertavex.core.network.dto.LogFileDto
import kotlinx.serialization.json.JsonElement
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import io.github.spasarnaudov.trackertavex.core.network.dto.ChangePasswordRequestDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.DELETE
import retrofit2.http.Multipart
import retrofit2.http.Part
import retrofit2.http.PUT
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Every call documented in API.md is implemented exactly as described there. Calls
 * marked ASSUMED are not in API.md (which points to a missing openapi.yaml for the
 * full contract) and follow the same REST/versioning conventions as the documented
 * ones; see docs/API_INTEGRATION.md for the full list of assumptions.
 */
interface ApiService {

    @GET("health")
    suspend fun health(): Response<HealthResponseDto>

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequestDto): Response<LoginResponseDto>

    /** ASSUMED: modeled on the documented login contract. */
    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequestDto): Response<LoginResponseDto>

    @POST("auth/logout")
    suspend fun logout(): Response<Unit>

    /**
     * Used both for startup session validation and for the account screen's "current
     * user" view (API.md doesn't separate these into two endpoints).
     */
    @GET("auth/session")
    suspend fun getCurrentUser(): Response<SessionResponseDto>

    /** ASSUMED. */
    @PUT("account/password")
    suspend fun changePassword(@Body request: ChangePasswordRequestDto): Response<Unit>

    /** ASSUMED. */
    @DELETE("account")
    suspend fun deactivateAccount(): Response<Unit>

    /** ASSUMED response shape; the PUT request shape is exactly as documented. */
    @GET("portfolio")
    suspend fun getPortfolio(): Response<PortfolioResponseDto>

    @PUT("portfolio")
    suspend fun updatePortfolio(@Body request: PortfolioUpdateRequestDto): Response<PortfolioResponseDto>

    @GET("portfolio/holdings/{assetId}/purchases")
    suspend fun getAssetPurchases(@Path("assetId") assetId: Long): Response<AssetPurchasesResponseDto>

    @POST("portfolio/holdings/{assetId}/purchases")
    suspend fun createAssetPurchase(
        @Path("assetId") assetId: Long,
        @Body request: AssetPurchaseRequestDto,
    ): Response<AssetPurchaseResponseDto>

    @PUT("portfolio/purchases/{purchaseId}")
    suspend fun updateAssetPurchase(
        @Path("purchaseId") purchaseId: Long,
        @Body request: AssetPurchaseRequestDto,
    ): Response<AssetPurchaseResponseDto>

    @DELETE("portfolio/purchases/{purchaseId}")
    suspend fun deleteAssetPurchase(@Path("purchaseId") purchaseId: Long): Response<Unit>

    @Multipart
    @POST("portfolio/purchases/{purchaseId}/receipt")
    suspend fun uploadPurchaseReceipt(
        @Path("purchaseId") purchaseId: Long,
        @Part receipt: MultipartBody.Part,
    ): Response<Unit>

    @DELETE("portfolio/purchases/{purchaseId}/receipt")
    suspend fun deletePurchaseReceipt(@Path("purchaseId") purchaseId: Long): Response<Unit>

    /** ASSUMED response shape (bare array of points). */
    @GET("portfolio/history")
    suspend fun getPortfolioHistory(
        @Query("range") range: String,
        @Query("interval") interval: String,
    ): Response<PortfolioHistoryResponseDto>

    /** ASSUMED response shape. */
    @GET("assets")
    suspend fun getAssets(): Response<AssetsResponseDto>

    /** ASSUMED response shape (bare array of points). */
    @GET("assets/{assetId}/prices")
    suspend fun getAssetPrices(
        @Path("assetId") assetId: Long,
        @Query("range") range: String,
        @Query("interval") interval: String,
        @Query("start_date") startDate: String? = null,
        @Query("end_date") endDate: String? = null,
    ): Response<AssetPricesResponseDto>

    /** ASSUMED response/request shape. */
    @GET("charts/configuration")
    suspend fun getChartConfiguration(): Response<ChartConfigurationDto>

    /** ASSUMED response/request shape. */
    @PUT("charts/configuration")
    suspend fun updateChartConfiguration(@Body request: ChartConfigurationDto): Response<ChartConfigurationDto>

    /** ASSUMED endpoint. */
    @GET("admin/users")
    suspend fun getAdminUsers(): Response<List<AdminUserDto>>

    /** ASSUMED endpoint; schema unknown so it's decoded generically. */
    @GET("admin/login-stats")
    suspend fun getAdminLoginStats(): Response<Map<String, JsonElement>>

    /** ASSUMED endpoint; schema unknown so it's decoded generically. */
    @GET("admin/login-history")
    suspend fun getAdminLoginHistory(): Response<List<Map<String, JsonElement>>>

    /** ASSUMED endpoint. */
    @GET("admin/logs")
    suspend fun getAdminLogFiles(): Response<List<LogFileDto>>

    /** ASSUMED endpoint; log content is returned as plain text. */
    @GET("admin/logs/{name}")
    suspend fun getAdminLogContent(@Path("name") name: String): Response<ResponseBody>
}
