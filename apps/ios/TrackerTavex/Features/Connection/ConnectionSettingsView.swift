import SwiftUI

/// Mirrors the Android client's "Login → Connection settings" screen: an override
/// for the API base URL, tested with a live GET /health call before saving.
struct ConnectionSettingsView: View {
    @ObservedObject var settings: AppSettings
    @Environment(\.dismiss) private var dismiss
    @State private var text: String = ""
    @State private var testResult: String?
    @State private var isTesting = false

    var body: some View {
        NavigationStack {
            Form {
                Section("API base URL") {
                    TextField(AppSettings.defaultBaseURL, text: $text)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .keyboardType(.URL)

                    if let testResult {
                        Text(testResult)
                            .font(.footnote)
                    }

                    Button {
                        Task { await testConnection() }
                    } label: {
                        if isTesting {
                            ProgressView()
                        } else {
                            Text("Test connection")
                        }
                    }
                    .disabled(!AppSettings.isValid(text) || isTesting)
                }

                Section {
                    Text("Must start with http:// or https:// and end with /.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
            .materialList()
            .navigationTitle("Connection settings")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Reset to default") {
                        settings.baseURLOverride = nil
                        text = ""
                    }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        settings.baseURLOverride = AppSettings.isValid(text) ? text : nil
                        dismiss()
                    }
                    .disabled(!AppSettings.isValid(text))
                }
            }
            .onAppear { text = settings.baseURLOverride ?? settings.baseURL }
        }
    }

    private func testConnection() async {
        isTesting = true
        testResult = nil
        defer { isTesting = false }
        let baseURL = text
        let client = APIClient(baseURLProvider: { baseURL }, tokenStorage: KeychainTokenStorage())
        do {
            let health = try await client.health()
            testResult = "Connected (\(health.status), api \(health.apiVersion))."
        } catch let error as AppError {
            testResult = error.message
        } catch {
            testResult = error.localizedDescription
        }
    }
}
