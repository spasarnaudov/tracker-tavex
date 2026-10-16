import Foundation

@MainActor
final class PortfolioHistoryViewModel: ObservableObject {
    static let ranges = ["1d", "1w", "1m", "ytd", "1y", "all"]
    static let intervals = ["hourly", "daily", "weekly"]

    @Published var range = "1m"
    @Published var interval = "daily"
    @Published private(set) var points: [PortfolioHistoryPoint] = []
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
            let response = try await session.apiClient.portfolioHistory(range: range, interval: interval)
            points = response.points
        } catch let error as AppError {
            errorMessage = error.message
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
