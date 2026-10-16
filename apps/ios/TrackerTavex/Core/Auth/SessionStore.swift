import Foundation

/// Holds the current auth state and owns the shared `APIClient`, similar in spirit to
/// the Android client's `SessionManager` + `AuthRepository`.
@MainActor
final class SessionStore: ObservableObject {
    enum Status {
        case checking
        case loggedOut
        case loggedIn
    }

    @Published private(set) var status: Status = .checking
    @Published private(set) var currentUser: PublicUser?

    let apiClient: APIClient
    private let tokenStorage: any TokenStoring

    init(settings: AppSettings, tokenStorage: any TokenStoring = KeychainTokenStorage()) {
        self.tokenStorage = tokenStorage
        self.apiClient = APIClient(baseURLProvider: { settings.baseURL }, tokenStorage: tokenStorage)
    }

    /// Called once on launch: GET /auth/session decides Login vs. Portfolio,
    /// mirroring the Android Splash screen's session-restore flow.
    func restoreSession() async {
        guard await tokenStorage.token != nil else {
            status = .loggedOut
            return
        }
        do {
            let response = try await apiClient.session()
            currentUser = response.user
            status = .loggedIn
        } catch {
            status = .loggedOut
        }
    }

    func login(username: String, password: String, force: Bool) async throws {
        let response = try await apiClient.login(username: username, password: password, force: force)
        await tokenStorage.save(response.token)
        currentUser = response.user
        status = .loggedIn
    }

    func register(username: String, password: String) async throws {
        let response = try await apiClient.register(username: username, password: password)
        await tokenStorage.save(response.token)
        currentUser = response.user
        status = .loggedIn
    }

    func logout() async {
        try? await apiClient.logout()
        await tokenStorage.clear()
        currentUser = nil
        status = .loggedOut
    }

    /// Ends the session locally only after the server confirms the account was
    /// deactivated — a failed `DELETE /account` (e.g. the last admin account)
    /// must not log the user out.
    func deactivateAccount() async throws {
        try await apiClient.deleteAccount()
        await tokenStorage.clear()
        currentUser = nil
        status = .loggedOut
    }
}
