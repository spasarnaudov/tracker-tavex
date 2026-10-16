import SwiftUI

struct AccountView: View {
    @StateObject private var viewModel: AccountViewModel
    @State private var showDeactivateConfirmation = false

    init(session: SessionStore) {
        _viewModel = StateObject(wrappedValue: AccountViewModel(session: session))
    }

    var body: some View {
        NavigationStack {
            Form {
                if let user = viewModel.currentUser {
                    Section("Account") {
                        LabeledContent("Username", value: user.username)
                        LabeledContent("Role", value: user.role)
                    }
                }

                Section("Change password") {
                    SecureField("Current password", text: $viewModel.currentPassword)
                    SecureField("New password (min. 8 characters)", text: $viewModel.newPassword)

                    if let message = viewModel.changePasswordMessage {
                        Text(message).foregroundStyle(MaterialColor.primary)
                    }
                    if let error = viewModel.changePasswordError {
                        Text(error).foregroundStyle(MaterialColor.error)
                    }

                    Button {
                        Task { await viewModel.changePassword() }
                    } label: {
                        if viewModel.isChangingPassword {
                            ProgressView()
                        } else {
                            Text("Change password")
                                .font(MaterialFont.labelLarge)
                                .foregroundStyle(MaterialColor.primary)
                        }
                    }
                    .disabled(
                        viewModel.currentPassword.isEmpty
                            || viewModel.newPassword.isEmpty
                            || viewModel.isChangingPassword
                    )
                }

                Section {
                    Button("Log out") {
                        Task { await viewModel.logout() }
                    }
                    .font(MaterialFont.labelLarge)
                    .foregroundStyle(MaterialColor.primary)
                }

                Section {
                    if let error = viewModel.deactivateError {
                        Text(error).foregroundStyle(MaterialColor.error)
                    }
                    Button("Deactivate account", role: .destructive) {
                        showDeactivateConfirmation = true
                    }
                    .font(MaterialFont.labelLarge)
                    .foregroundStyle(MaterialColor.error)
                    .disabled(viewModel.isDeactivating)
                } footer: {
                    Text("This deactivates your account and signs you out. It cannot be undone from the app.")
                }
            }
            .materialList()
            .navigationTitle("Account")
            .confirmationDialog(
                "Deactivate your account?",
                isPresented: $showDeactivateConfirmation,
                titleVisibility: .visible
            ) {
                Button("Deactivate", role: .destructive) {
                    Task { await viewModel.deactivateAccount() }
                }
                Button("Cancel", role: .cancel) {}
            }
        }
    }
}
