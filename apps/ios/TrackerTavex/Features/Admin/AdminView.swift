import SwiftUI

struct AdminView: View {
    @ObservedObject var session: SessionStore
    @StateObject private var viewModel: AdminViewModel

    init(session: SessionStore) {
        self.session = session
        _viewModel = StateObject(wrappedValue: AdminViewModel(session: session))
    }

    var body: some View {
        NavigationStack {
            List {
                Section {
                    ForEach(viewModel.users) { user in
                        VStack(alignment: .leading, spacing: 2) {
                            HStack {
                                Text(user.username)
                                    .font(MaterialFont.titleSmall)
                                    .foregroundStyle(MaterialColor.onSurface)
                                if user.isDeleted {
                                    Text("Deactivated")
                                        .font(MaterialFont.labelSmall)
                                        .foregroundStyle(MaterialColor.error)
                                }
                            }
                            Text("\(user.role) · \(user.loginCount) logins · last: \(user.lastLoginAt ?? "never")")
                                .font(MaterialFont.bodyMedium)
                                .foregroundStyle(MaterialColor.onSurfaceVariant)
                        }
                    }
                } header: {
                    Text("Users").font(MaterialFont.labelLarge)
                }

                Section {
                    ForEach(viewModel.loginHistory) { entry in
                        HStack {
                            Text(entry.username)
                                .font(MaterialFont.bodyLarge)
                                .foregroundStyle(MaterialColor.onSurface)
                            Spacer()
                            Text(entry.loggedInAt)
                                .font(MaterialFont.bodyMedium)
                                .foregroundStyle(MaterialColor.onSurfaceVariant)
                        }
                    }
                } header: {
                    Text("Login history").font(MaterialFont.labelLarge)
                }

                Section {
                    ForEach(viewModel.logFiles) { file in
                        NavigationLink(value: file) {
                            VStack(alignment: .leading) {
                                Text(file.label)
                                    .font(MaterialFont.bodyLarge)
                                    .foregroundStyle(MaterialColor.onSurface)
                                Text("\(file.displayedLines) of \(file.totalLines) lines")
                                    .font(MaterialFont.bodyMedium)
                                    .foregroundStyle(MaterialColor.onSurfaceVariant)
                            }
                        }
                    }
                } header: {
                    Text("Logs").font(MaterialFont.labelLarge)
                }

                if let errorMessage = viewModel.errorMessage {
                    Section {
                        Text(errorMessage)
                            .foregroundStyle(MaterialColor.error)
                    }
                }
            }
            .materialList()
            .navigationTitle("Admin")
            .navigationDestination(for: AdminLogFile.self) { file in
                AdminLogDetailView(session: session, logFile: file)
            }
            .refreshable { await viewModel.load() }
            .overlay {
                if viewModel.isLoading && viewModel.users.isEmpty {
                    ProgressView()
                }
            }
            .task { await viewModel.load() }
        }
    }
}
