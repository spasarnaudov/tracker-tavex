import SwiftUI

/// A subset of the Material 3 type scale (sizes/weights), matching what
/// `apps/android/.../ui/theme/Type.kt` uses from Compose's default `Typography()`.
enum MaterialFont {
    static let headlineSmall = Font.system(size: 24, weight: .regular)
    static let titleLarge = Font.system(size: 22, weight: .regular)
    static let titleMedium = Font.system(size: 16, weight: .medium)
    static let titleSmall = Font.system(size: 14, weight: .medium)
    static let bodyLarge = Font.system(size: 16, weight: .regular)
    static let bodyMedium = Font.system(size: 14, weight: .regular)
    static let labelLarge = Font.system(size: 14, weight: .medium)
    static let labelSmall = Font.system(size: 11, weight: .medium)
}
