import SwiftUI

struct LoginView: View {
    @ObservedObject var session: SessionStore
    @ObservedObject var settings: AppSettings
    @StateObject private var viewModel: LoginViewModel
    @State private var showConnectionSettings = false
    @State private var showRegister = false

    init(session: SessionStore, settings: AppSettings) {
        self.session = session
        self.settings = settings
        _viewModel = StateObject(wrappedValue: LoginViewModel(session: session))
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 16) {
                Text("Tracker Tavex")
                    .font(MaterialFont.headlineSmall)
                    .foregroundStyle(MaterialColor.onSurface)

                TextField("Username", text: $viewModel.username)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .textFieldStyle(.materialOutlined)

                SecureField("Password", text: $viewModel.password)
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
                        Text("Log In")
                    }
                }
                .buttonStyle(.materialFilled)
                .disabled(viewModel.username.isEmpty || viewModel.password.isEmpty || viewModel.isLoading)

                Button("Create an account") { showRegister = true }
                    .font(MaterialFont.labelLarge)
                    .foregroundStyle(MaterialColor.primary)
            }
            .padding()
            .background(MaterialColor.background)
            .navigationTitle("")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Connection") { showConnectionSettings = true }
                }
            }
            .sheet(isPresented: $showConnectionSettings) {
                ConnectionSettingsView(settings: settings)
            }
            .sheet(isPresented: $showRegister) {
                RegisterView(session: session)
            }
            .alert("Active session", isPresented: $viewModel.showForceLoginConfirmation) {
                Button("Cancel", role: .cancel) {}
                Button("Continue") { Task { await viewModel.confirmForceLogin() } }
            } message: {
                Text("This account is already logged in elsewhere. Log in here and end that session?")
            }
            .task {
                // Best-effort connection warm-up: the backend is a Tailscale-only host, so the
                // very first request in a session pays for DNS + TLS/TCP setup over the VPN.
                // Firing it here, while the user is still typing, keeps that cost off the
                // critical path of tapping "Log In".
                _ = try? await session.apiClient.health()
            }
        }
    }
}
