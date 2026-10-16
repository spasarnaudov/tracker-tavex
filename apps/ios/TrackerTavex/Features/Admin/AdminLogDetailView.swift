import SwiftUI

struct AdminLogDetailView: View {
    @ObservedObject var session: SessionStore
    let logFile: AdminLogFile
    @State private var content: String = ""
    @State private var isLoading = false
    @State private var errorMessage: String?

    var body: some View {
        ScrollView {
            Text(content.isEmpty ? logFile.content : content)
                .font(.system(.footnote, design: .monospaced))
                .foregroundStyle(MaterialColor.onSurface)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding()
        }
        .background(MaterialColor.background)
        .navigationTitle(logFile.label)
        .overlay {
            if isLoading && content.isEmpty && logFile.content.isEmpty {
                ProgressView()
            }
        }
        .safeAreaInset(edge: .bottom) {
            if let errorMessage {
                Text(errorMessage)
                    .foregroundStyle(MaterialColor.error)
                    .padding()
            }
        }
        .task { await loadContent() }
    }

    private func loadContent() async {
        isLoading = true
        defer { isLoading = false }
        do {
            content = try await session.apiClient.adminLogContent(name: logFile.name)
        } catch let error as AppError {
            errorMessage = error.message
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
