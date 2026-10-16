import SwiftUI

@main
struct TrackerTavexApp: App {
    init() {
        Self.configureMaterialAppearance()
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .tint(MaterialColor.primary)
        }
    }

    /// Recolors the system chrome (nav bars, tab bar, segmented control) to the same
    /// Material 3 baseline palette as the Android client, since UIKit appearance
    /// proxies — not SwiftUI view modifiers — are what actually control these.
    private static func configureMaterialAppearance() {
        let navigationBarAppearance = UINavigationBarAppearance()
        navigationBarAppearance.configureWithOpaqueBackground()
        navigationBarAppearance.backgroundColor = MaterialColor.uiSurface
        navigationBarAppearance.titleTextAttributes = [.foregroundColor: MaterialColor.uiOnSurface]
        navigationBarAppearance.largeTitleTextAttributes = [.foregroundColor: MaterialColor.uiOnSurface]
        UINavigationBar.appearance().standardAppearance = navigationBarAppearance
        UINavigationBar.appearance().scrollEdgeAppearance = navigationBarAppearance
        UINavigationBar.appearance().compactAppearance = navigationBarAppearance
        UINavigationBar.appearance().tintColor = MaterialColor.uiPrimary

        let tabBarAppearance = UITabBarAppearance()
        tabBarAppearance.configureWithOpaqueBackground()
        tabBarAppearance.backgroundColor = MaterialColor.uiSurface
        UITabBar.appearance().standardAppearance = tabBarAppearance
        UITabBar.appearance().scrollEdgeAppearance = tabBarAppearance
        UITabBar.appearance().tintColor = MaterialColor.uiPrimary

        UISegmentedControl.appearance().selectedSegmentTintColor = MaterialColor.uiPrimary
        UISegmentedControl.appearance().setTitleTextAttributes([.foregroundColor: MaterialColor.uiOnPrimary], for: .selected)
    }
}
