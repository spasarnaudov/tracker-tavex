import Foundation

@MainActor
final class RegisterViewModel: ObservableObject {
    @Published var username = ""
    @Published var password = ""
    @Published var confirmPassword = ""
    @Published var isLoading = false
    @Published var errorMessage: String?

    private let session: SessionStore

    init(session: SessionStore) {
        self.session = session
    }

    var canSubmit: Bool {
        username.count >= 3 && password.count >= 8 && password == confirmPassword && !isLoading
    }

    func submit() async {
        errorMessage = nil
        guard password == confirmPassword else {
            errorMessage = "Passwords do not match."
            return
        }
        isLoading = true
        defer { isLoading = false }
        do {
            try await session.register(username: username, password: password)
        } catch let error as AppError {
            errorMessage = error.message
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
