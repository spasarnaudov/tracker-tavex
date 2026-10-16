import SwiftUI
import Charts

/// Embeddable content (not its own screen) — placed as a `Section` inside the
/// Assets list rather than behind a separate button/sheet on Portfolio.
struct PortfolioHistoryView: View {
    @StateObject private var viewModel: PortfolioHistoryViewModel

    init(session: SessionStore) {
        _viewModel = StateObject(wrappedValue: PortfolioHistoryViewModel(session: session))
    }

    var body: some View {
        Group {
            Picker("Range", selection: $viewModel.range) {
                ForEach(PortfolioHistoryViewModel.ranges, id: \.self) { Text($0) }
            }
            .pickerStyle(.segmented)

            Picker("Interval", selection: $viewModel.interval) {
                ForEach(PortfolioHistoryViewModel.intervals, id: \.self) { Text($0) }
            }

            if viewModel.points.isEmpty {
                if viewModel.isLoading {
                    ProgressView()
                } else {
                    Text("No history for this range.")
                        .foregroundStyle(MaterialColor.onSurfaceVariant)
                }
            } else {
                Chart(viewModel.points) { point in
                    if let value = point.value, let date = DateParsing.parse(point.priceDate) {
                        LineMark(x: .value("Date", date), y: .value("Value", value))
                            .foregroundStyle(MaterialColor.primary)
                    }
                }
                .frame(height: 220)
            }

            if let errorMessage = viewModel.errorMessage {
                Text(errorMessage)
                    .foregroundStyle(MaterialColor.error)
            }
        }
        .onChange(of: viewModel.range) { _, _ in Task { await viewModel.load() } }
        .onChange(of: viewModel.interval) { _, _ in Task { await viewModel.load() } }
        .task { await viewModel.load() }
    }
}
