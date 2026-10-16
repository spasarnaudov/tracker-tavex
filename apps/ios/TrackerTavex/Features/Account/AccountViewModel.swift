import Foundation

@MainActor
final class AccountViewModel: ObservableObject {
    @Published var currentPassword = ""
    @Published var newPassword = ""
    @Published var isChangingPassword = false
    @Published var changePasswordMessage: String?
    @Published var changePasswordError: String?

    @Published var isDeactivating = false
    @Published var deactivateError: String?

    private let session: SessionStore

    init(session: SessionStore) {
        self.session = session
    }

    var currentUser: PublicUser? { session.currentUser }

    func changePassword() async {
        changePasswordError = nil
        changePasswordMessage = nil
        guard newPassword.count >= 8 else {
            changePasswordError = "New password must be at least 8 characters."
            return
        }
        isChangingPassword = true
        defer { isChangingPassword = false }
        do {
            let response = try await session.apiClient.changePassword(
                currentPassword: currentPassword,
                newPassword: newPassword
            )
            changePasswordMessage = response.message
            currentPassword = ""
            newPassword = ""
        } catch let error as AppError {
            changePasswordError = error.message
        } catch {
            changePasswordError = error.localizedDescription
        }
    }

    func deactivateAccount() async {
        deactivateError = nil
        isDeactivating = true
        defer { isDeactivating = false }
        do {
            try await session.deactivateAccount()
        } catch let error as AppError {
            deactivateError = error.message
        } catch {
            deactivateError = error.localizedDescription
        }
    }

    func logout() async {
        await session.logout()
    }
}
