import SwiftUI
import UIKit

/// The Material 3 "baseline" role colors, using the same primary/secondary/tertiary
/// seed hues as the Android client's unmodified Compose template theme
/// (`apps/android/.../ui/theme/Color.kt` — Purple40/PurpleGrey40/Pink40 in light,
/// Purple80/PurpleGrey80/Pink80 in dark). Android also layers Material You dynamic
/// color from the wallpaper on API 31+; this fixed baseline palette is the closest
/// iOS equivalent since there's no per-device wallpaper-derived scheme here.
private extension UIColor {
    convenience init(hex: UInt32) {
        self.init(
            red: CGFloat((hex >> 16) & 0xFF) / 255,
            green: CGFloat((hex >> 8) & 0xFF) / 255,
            blue: CGFloat(hex & 0xFF) / 255,
            alpha: 1
        )
    }
}

private func dynamic(light: UInt32, dark: UInt32) -> Color {
    Color(UIColor { traits in
        traits.userInterfaceStyle == .dark ? UIColor(hex: dark) : UIColor(hex: light)
    })
}

enum MaterialColor {
    static let primary = dynamic(light: 0x6650A4, dark: 0xD0BCFF)
    static let onPrimary = dynamic(light: 0xFFFFFF, dark: 0x381E72)
    static let primaryContainer = dynamic(light: 0xEADDFF, dark: 0x4F378B)
    static let onPrimaryContainer = dynamic(light: 0x21005D, dark: 0xEADDFF)

    static let secondary = dynamic(light: 0x625B71, dark: 0xCCC2DC)
    static let onSecondary = dynamic(light: 0xFFFFFF, dark: 0x332D41)
    static let secondaryContainer = dynamic(light: 0xE8DEF8, dark: 0x4A4458)
    static let onSecondaryContainer = dynamic(light: 0x1D192B, dark: 0xE8DEF8)

    static let tertiary = dynamic(light: 0x7D5260, dark: 0xEFB8C8)
    static let onTertiary = dynamic(light: 0xFFFFFF, dark: 0x492532)

    static let error = dynamic(light: 0xB3261E, dark: 0xF2B8B5)
    static let onError = dynamic(light: 0xFFFFFF, dark: 0x601410)
    static let errorContainer = dynamic(light: 0xF9DEDC, dark: 0x8C1D18)
    static let onErrorContainer = dynamic(light: 0x410E0B, dark: 0xF9DEDC)

    static let background = dynamic(light: 0xFFFBFE, dark: 0x1C1B1F)
    static let onBackground = dynamic(light: 0x1C1B1F, dark: 0xE6E1E5)
    static let surface = dynamic(light: 0xFFFBFE, dark: 0x1C1B1F)
    static let onSurface = dynamic(light: 0x1C1B1F, dark: 0xE6E1E5)
    static let surfaceVariant = dynamic(light: 0xE7E0EC, dark: 0x49454F)
    static let onSurfaceVariant = dynamic(light: 0x49454F, dark: 0xCAC4D0)
    static let outline = dynamic(light: 0x79747E, dark: 0x938F99)

    static let uiPrimary = UIColor { $0.userInterfaceStyle == .dark ? UIColor(hex: 0xD0BCFF) : UIColor(hex: 0x6650A4) }
    static let uiOnPrimary = UIColor { $0.userInterfaceStyle == .dark ? UIColor(hex: 0x381E72) : UIColor(hex: 0xFFFFFF) }
    static let uiSurface = UIColor { $0.userInterfaceStyle == .dark ? UIColor(hex: 0x1C1B1F) : UIColor(hex: 0xFFFBFE) }
    static let uiOnSurface = UIColor { $0.userInterfaceStyle == .dark ? UIColor(hex: 0xE6E1E5) : UIColor(hex: 0x1C1B1F) }
}
