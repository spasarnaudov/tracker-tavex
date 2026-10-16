import SwiftUI

/// Material 3 "Filled Button": pill shape, primary background, onPrimary label —
/// the equivalent of Compose's `Button` (`ButtonDefaults.filledButtonColors`).
struct MaterialFilledButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(MaterialFont.labelLarge)
            .foregroundStyle(MaterialColor.onPrimary)
            .padding(.vertical, 12)
            .frame(maxWidth: .infinity)
            .background(MaterialColor.primary.opacity(configuration.isPressed ? 0.85 : 1))
            .clipShape(Capsule())
    }
}

/// Material 3 "Tonal Button": pill shape, secondary-container background — used for
/// less prominent actions than a Filled Button.
struct MaterialTonalButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(MaterialFont.labelLarge)
            .foregroundStyle(MaterialColor.onSecondaryContainer)
            .padding(.vertical, 10)
            .padding(.horizontal, 20)
            .background(MaterialColor.secondaryContainer.opacity(configuration.isPressed ? 0.85 : 1))
            .clipShape(Capsule())
    }
}

extension ButtonStyle where Self == MaterialFilledButtonStyle {
    static var materialFilled: MaterialFilledButtonStyle { MaterialFilledButtonStyle() }
}

extension ButtonStyle where Self == MaterialTonalButtonStyle {
    static var materialTonal: MaterialTonalButtonStyle { MaterialTonalButtonStyle() }
}

/// Material 3 "Outlined TextField": 4dp corner radius, 1pt outline stroke.
struct MaterialTextFieldStyle: TextFieldStyle {
    func _body(configuration: TextField<Self._Label>) -> some View {
        configuration
            .font(MaterialFont.bodyLarge)
            .padding(.horizontal, 12)
            .padding(.vertical, 12)
            .background(
                RoundedRectangle(cornerRadius: 4, style: .continuous)
                    .strokeBorder(MaterialColor.outline, lineWidth: 1)
            )
    }
}

extension TextFieldStyle where Self == MaterialTextFieldStyle {
    static var materialOutlined: MaterialTextFieldStyle { MaterialTextFieldStyle() }
}

/// Applies the Material list-row look everywhere a `List` is used: an inset,
/// rounded-section style (the closest native equivalent of Material Cards grouping
/// list content) plus Material's own background/surface colors instead of the
/// system ones.
struct MaterialListStyle: ViewModifier {
    func body(content: Content) -> some View {
        content
            .listStyle(.insetGrouped)
            .scrollContentBackground(.hidden)
            .background(MaterialColor.background)
            .listRowBackground(MaterialColor.surface)
    }
}

extension View {
    func materialList() -> some View {
        modifier(MaterialListStyle())
    }
}
