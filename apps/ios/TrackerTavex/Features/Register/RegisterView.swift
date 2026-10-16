import SwiftUI

struct RegisterView: View {
    @StateObject private var viewModel: RegisterViewModel

    init(session: SessionStore) {
        _viewModel = StateObject(wrappedValue: RegisterViewModel(session: session))
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 16) {
                Text("Create account")
                    .font(MaterialFont.titleLarge)
                    .foregroundStyle(MaterialColor.onSurface)

                TextField("Username (min. 3 characters)", text: $viewModel.username)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .textFieldStyle(.materialOutlined)

                SecureField("Password (min. 8 characters)", text: $viewModel.password)
                    .textFieldStyle(.materialOutlined)

                SecureField("Confirm password", text: $viewModel.confirmPassword)
                    .textFieldStyle(.materialOutlined)

                if let errorMessage = viewModel.errorMessage {
                    Text(errorMessage)
                        .foregroundStyle(MaterialColor.error)
                        .font(MaterialFont.bodyMedium)
                }

                Button {
                    Task { await viewModel.submit() }
                } label: {
                    if viewModel.isLoading {
                        ProgressView()
                            .tint(MaterialColor.onPrimary)
                    } else {
                        Text("Register")
                    }
                }
                .buttonStyle(.materialFilled)
                .disabled(!viewModel.canSubmit)
            }
            .padding()
            .background(MaterialColor.background)
            .navigationTitle("Register")
            .navigationBarTitleDisplayMode(.inline)
        }
    }
}
