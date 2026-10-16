import Foundation

@MainActor
final class AssetDetailViewModel: ObservableObject {
    static let ranges = ["1d", "1w", "1m", "ytd", "1y", "all"]
    static let intervals = ["recorded", "hourly", "daily", "weekly", "monthly"]

    @Published var range = "1m"
    @Published var interval = "daily"
    @Published private(set) var prices: [PricePoint] = []
    @Published private(set) var isLoading = false
    @Published var errorMessage: String?

    private let session: SessionStore
    private let assetId: Int

    init(session: SessionStore, assetId: Int) {
        self.session = session
        self.assetId = assetId
    }

    func load() async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }
        do {
            let response = try await session.apiClient.assetPrices(assetId: assetId, range: range, interval: interval)
            prices = response.prices
        } catch let error as AppError {
            errorMessage = error.message
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
