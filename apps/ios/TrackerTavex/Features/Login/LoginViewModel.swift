import Foundation

@MainActor
final class LoginViewModel: ObservableObject {
    @Published var username = ""
    @Published var password = ""
    @Published var isLoading = false
    @Published var errorMessage: String?
    @Published var showForceLoginConfirmation = false

    private let session: SessionStore

    init(session: SessionStore) {
        self.session = session
    }

    func submit() async {
        await attemptLogin(force: false)
    }

    func confirmForceLogin() async {
        showForceLoginConfirmation = false
        await attemptLogin(force: true)
    }

    private func attemptLogin(force: Bool) async {
        errorMessage = nil
        isLoading = true
        defer { isLoading = false }
        do {
            try await session.login(username: username, password: password, force: force)
        } catch AppError.activeSessionConflict {
            // Matches the server's 409 active_session response — same force-login
            // confirmation flow as the Android client (docs/API_INTEGRATION.md).
            showForceLoginConfirmation = true
        } catch let error as AppError {
            errorMessage = error.message
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
