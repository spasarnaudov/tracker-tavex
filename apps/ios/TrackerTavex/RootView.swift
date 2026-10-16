import SwiftUI

struct RootView: View {
    @StateObject private var settings: AppSettings
    @StateObject private var session: SessionStore

    init() {
        let settings = AppSettings()
        _settings = StateObject(wrappedValue: settings)
        _session = StateObject(wrappedValue: SessionStore(settings: settings))
    }

    var body: some View {
        Group {
            switch session.status {
            case .checking:
                ProgressView("Loading...")
                    .task { await session.restoreSession() }
            case .loggedOut:
                LoginView(session: session, settings: settings)
            case .loggedIn:
                MainTabView(session: session)
            }
        }
    }
}
