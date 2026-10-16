import Foundation

// Request/response shapes below mirror `apps/flask/api.py` and `apps/flask/repository.py`
// directly (the real server contract), not the assumptions documented for the Android
// client in `apps/android/docs/API_INTEGRATION.md`.

struct HealthResponse: Decodable {
    let status: String
    let apiVersion: String
}

struct PublicUser: Codable, Equatable {
    let id: Int
    let username: String
    let role: String
}

struct LoginRequest: Encodable {
    let username: String
    let password: String
    let force: Bool?
}

struct AuthResponse: Decodable {
    let token: String
    let tokenType: String
    let expiresAt: String
    let user: PublicUser
}

struct SessionResponse: Decodable {
    let authenticated: Bool
    let user: PublicUser
}

struct Holding: Decodable, Identifiable {
    var id: Int { assetId }
    let assetId: Int
    let symbol: String
    let name: String
    let quantity: Double
    let includeInChart: Bool
    let priceDate: String?
    let price: Double?
    let currentValue: Double?
}

struct ManualItem: Decodable, Identifiable {
    let id: Int
    let name: String
    let quantity: Double
    let unitPrice: Double
    let priceAssetId: Int?
    let includeInChart: Bool
    let currentValue: Double?
}

struct GoldBuybackAsset: Decodable, Identifiable {
    let id: Int
    let name: String
    let price: Double?
}

struct PortfolioResponse: Decodable {
    let holdings: [Holding]
    let manualItems: [ManualItem]
    let totalValue: Double
    let goldBuybackAssets: [GoldBuybackAsset]
}

struct UpdateHoldingRequest: Encodable {
    let assetId: Int
    let quantity: Double
    let includeInChart: Bool
}

struct UpdateManualItemRequest: Encodable {
    let id: Int?
    let name: String
    let quantity: Double
    let unitPrice: Double
    let priceAssetId: Int?
    let includeInChart: Bool
    let delete: Bool
}

struct UpdatePortfolioRequest: Encodable {
    let holdings: [UpdateHoldingRequest]
    let manualItems: [UpdateManualItemRequest]
}

struct RegisterRequest: Encodable {
    let username: String
    let password: String
}

struct ChangePasswordRequest: Encodable {
    let currentPassword: String
    let newPassword: String
}

struct MessageResponse: Decodable {
    let message: String
}

struct Asset: Decodable, Identifiable, Hashable {
    let id: Int
    let symbol: String
    let name: String
}

struct AssetsResponse: Decodable {
    let assets: [Asset]
    let goldBuybackAssets: [GoldBuybackAsset]
}

struct PricePoint: Decodable, Identifiable {
    var id: String { priceDate }
    let priceDate: String
    let price: Double?
}

struct AssetPricesResponse: Decodable {
    let assetId: Int
    let range: String
    let interval: String
    let startDate: String?
    let endDate: String?
    let prices: [PricePoint]
}

struct PortfolioHistoryPoint: Decodable, Identifiable {
    var id: String { priceDate }
    let priceDate: String
    let value: Double?
}

struct PortfolioHistoryResponse: Decodable {
    let range: String
    let interval: String
    let startDate: String?
    let endDate: String?
    let points: [PortfolioHistoryPoint]
}

struct AdminUser: Decodable, Identifiable {
    let id: Int
    let username: String
    let role: String
    let isDeleted: Bool
    let createdAt: String
    let loginCount: Int
    let lastLoginAt: String?
}

struct AdminLoginHistoryEntry: Decodable, Identifiable {
    let id: Int
    let userId: Int
    let username: String
    let loggedInAt: String
}

struct AdminLogFile: Decodable, Identifiable, Hashable {
    var id: String { name }
    let name: String
    let label: String
    let content: String
    let modifiedAt: String?
    let displayedLines: Int
    let totalLines: Int
    let error: String?
}
