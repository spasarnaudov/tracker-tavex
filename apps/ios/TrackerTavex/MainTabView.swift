import SwiftUI

/// Tabs after login. The server's role-manager admin account can't reach
/// portfolio/market-data endpoints (403 — see `require_api_auth` in
/// `apps/flask/api.py`), so it only sees Account + Admin, mirroring the same
/// restriction described for the Android client in apps/android/README.md.
///
/// SwiftUI's tab-bar-style `TabView` mounts every tab's content as soon as the
/// `TabView` itself appears, not just the selected one — right after login this
/// used to fire `GET /portfolio`, `GET /assets`, `GET /portfolio/history`, and
/// the account screen's setup all at once, which made the app feel frozen for a
/// few seconds on a Tailscale-latency connection. Every tab but the first/default
/// one is now only mounted the first time it's actually selected, and stays
/// mounted after that (so its state isn't lost by switching away and back).
struct MainTabView: View {
    @ObservedObject var session: SessionStore
    @State private var selectedTab = 0
    @State private var hasVisitedSecondTab = false
    @State private var hasVisitedThirdTab = false

    var body: some View {
        if session.currentUser?.role == "admin" {
            TabView(selection: $selectedTab) {
                AccountView(session: session)
                    .tabItem { Label("Account", systemImage: "person.circle") }
                    .tag(0)

                lazyTab(hasVisitedSecondTab) { AdminView(session: session) }
                    .tabItem { Label("Admin", systemImage: "wrench.and.screwdriver") }
                    .tag(1)
            }
            .onChange(of: selectedTab) { _, tab in
                if tab == 1 { hasVisitedSecondTab = true }
            }
        } else {
            TabView(selection: $selectedTab) {
                PortfolioView(session: session)
                    .tabItem { Label("Portfolio", systemImage: "chart.pie") }
                    .tag(0)

                lazyTab(hasVisitedSecondTab) { AssetsView(session: session) }
                    .tabItem { Label("Assets", systemImage: "list.bullet") }
                    .tag(1)

                lazyTab(hasVisitedThirdTab) { AccountView(session: session) }
                    .tabItem { Label("Account", systemImage: "person.circle") }
                    .tag(2)
            }
            .onChange(of: selectedTab) { _, tab in
                if tab == 1 { hasVisitedSecondTab = true }
                if tab == 2 { hasVisitedThirdTab = true }
            }
        }
    }

    @ViewBuilder
    private func lazyTab<Content: View>(_ hasVisited: Bool, @ViewBuilder content: () -> Content) -> some View {
        if hasVisited {
            content()
        } else {
            Color.clear
        }
    }
}
