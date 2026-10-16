import Foundation

@MainActor
final class AssetsViewModel: ObservableObject {
    @Published private(set) var assets: [Asset] = []
    @Published private(set) var goldBuybackAssets: [GoldBuybackAsset] = []
    @Published private(set) var isLoading = false
    @Published var errorMessage: String?

    private let session: SessionStore

    init(session: SessionStore) {
        self.session = session
    }

    func load() async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }
        do {
            let response = try await session.apiClient.assets()
            assets = response.assets
            goldBuybackAssets = response.goldBuybackAssets
        } catch let error as AppError {
            errorMessage = error.message
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
