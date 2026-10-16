import Foundation

@MainActor
final class AdminViewModel: ObservableObject {
    @Published private(set) var users: [AdminUser] = []
    @Published private(set) var loginHistory: [AdminLoginHistoryEntry] = []
    @Published private(set) var logFiles: [AdminLogFile] = []
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
            async let usersResult = session.apiClient.adminUsers()
            async let historyResult = session.apiClient.adminLoginHistory()
            async let logsResult = session.apiClient.adminLogFiles()
            let (users, history, logs) = try await (usersResult, historyResult, logsResult)
            self.users = users
            self.loginHistory = history
            self.logFiles = logs
        } catch let error as AppError {
            errorMessage = error.message
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
